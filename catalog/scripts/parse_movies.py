"""Parse the IMDb-style lists the user pasted into one structured catalog (movies.json).

Usage (from the repo root): python3 catalog/scripts/parse_movies.py catalog/source catalog
Add a movie: paste more IMDb list text into catalog/source/*.txt (or edit catalog/movies.json directly), then run
gen_catalog_sql.py to produce a new migration (copy it to the next free version, e.g. V102__more_movies.sql).
"""
import json
import re
import sys
from datetime import date
from pathlib import Path

DATA = Path(sys.argv[1])
OUT_DIR = Path(sys.argv[2]) if len(sys.argv) > 2 else DATA
TODAY = date(2026, 10, 30)

LISTS = [
    ("telugu-2026.txt", "Telugu"),
    ("hindi-2026.txt", "Hindi"),
    ("malayalam-2026.txt", "Malayalam"),
    ("tamil-2026.txt", "Tamil"),
    ("kannada-2026.txt", "Kannada"),
]
TOP250 = "top-250-all-time.txt"

MONTHS_EN = {m: i for i, m in enumerate(
    ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"], 1)}
MONTHS_HI = {"जन": 1, "फ़र": 2, "फर": 2, "मार्च": 3, "अप्रैल": 4, "मई": 5, "जून": 6, "जुल": 7, "अग": 8,
             "सित": 9, "अक्तू": 10, "अक्टू": 10, "नव": 11, "दिस": 12}

# Hindi-script titles in the Hindi list -> the film's English title
TITLE_FIX = {
    "रामायणम्": "Ramayana",
    "डकैत: एक प्रेम कथा": "Dacoit: A Love Story",
    "छत्रपती शिवाजी महाराज": "Chhatrapati Shivaji Maharaj",
    "गोरखा": "Gorkha",
}

# Synopses that arrived in Hindi: short English versions (written fresh, not translated verbatim)
SYNOPSIS_EN = {
    "Satluj": "The life of human-rights activist Jaswant Singh Khalra, who exposed thousands of enforced disappearances in Punjab.",
    "Dhurandhar: The Revenge": "Hamza Ali Mazari, really Indian operative Jaskirat Singh Rangi, goes deeper undercover inside Pakistan's criminal underworld.",
    "Border 2": "Young Indian soldiers stand their ground to defend the homeland during the 1971 India-Pakistan war.",
    "O' Romeo": "In post-independence Bombay an underworld takes shape, told through the streets and shadows of a city in transition.",
    "Subedaar": "A former soldier who once fought for the nation must now fight enemies at home to protect his family.",
    "Mardaani 3": "Shivani Shivaji Roy returns in a race against time to rescue girls who have vanished without a trace.",
    "Assi": "An investigation into a sexual-assault case exposes a broken system of red tape, bad faith and misused legal process.",
    "The Kerala Story 2 Goes Beyond": "Three young women choose love over tradition and find themselves trapped, as romance and rebellion turn into control.",
    "Tu Yaa Main": "Two rival content creators' adventure becomes a deadly fight for survival against nature and each other.",
    "Do Deewane Seher Mein": "Two socially awkward millennials in Mumbai find love while learning to accept themselves, travelling from city chaos to mountain calm.",
    "Gorkha": "Based on the life of Major General Ian Cardozo, a decorated officer of the Indian Army's Gorkha Rifles.",
}

SKIP_TITLES = {"Untitled Atlee/Salman Khan Project", "Untitled Filip Markovic Action Movie"}

INFO_RE = re.compile(r"^(?P<year>\d{4})(?:(?P<h>\d)h(?: (?P<m>\d{1,2})m)?|(?P<mo>\d{1,2})m)?(?P<cert>.*)$")
RATING_RE = re.compile(r"^\d\.\d$")
VOTES_RE = re.compile(r"^\((?P<v>[\d.]+)\s*(?P<u>K|M|हज़ार|लाख)?\)$")
TITLE_RE = re.compile(r"^(\d{1,3})\.\s+(.+)$")


def votes_to_int(text):
    m = VOTES_RE.match(text.strip())
    if not m:
        return None
    v = float(m.group("v"))
    mult = {"K": 1e3, "M": 1e6, "हज़ार": 1e3, "लाख": 1e5}.get(m.group("u"), 1)
    return int(round(v * mult))


def split_people(s):
    """IMDb copy-paste glues names together: 'ChiranjeeviNayantharaCatherine Tresa'."""
    s = s.strip()
    if not s:
        return []
    parts = re.split(r"(?<=[a-z.)])(?=[A-Z])", s)
    out = []
    for p in parts:
        p = p.strip()
        if p and p not in out:
            out.append(p)
    return out


def parse_credits(line):
    line = line.replace("निर्देशक", "Director").replace("स्टार्स", "Stars").replace("स्टार", "Star")
    m = re.match(r"^Directors?(.*?)(?:Stars?(.*))?$", line)
    if not m:
        return [], []
    directors = split_people(m.group(1) or "")
    stars = split_people(m.group(2) or "")
    return directors, stars


def parse_release(line):
    """Returns (year, release_date|None, runtime_min|None, cert|None, expected:bool) or None."""
    line = line.strip()
    expected = False
    rdate = None
    # English: "Releases Nov 6, 20261h 12m15" / "Releases Dec 24, 2026" / "Expected 20262h 25m15" / "Expected 2027"
    m = re.match(r"^Releases (\w{3}) (\d{1,2}), (.*)$", line)
    if m:
        rest = m.group(3)
        y = int(rest[:4])
        rdate = date(y, MONTHS_EN[m.group(1)], int(m.group(2)))
        line = rest
    elif line.startswith("Expected "):
        expected = True
        line = line[len("Expected "):]
    # Hindi: "रिलीज़ किया गया 5 नव॰ 2026" / "2026 अपेक्षित है" / "30 अप्रैल 2027 अपेक्षित है" / "दिस॰ 2026 अपेक्षित है"
    if "अपेक्षित" in line or "रिलीज़" in line:
        expected = "अपेक्षित" in line
        core = line.replace("रिलीज़ किया गया", "").replace("अपेक्षित है", "").replace("॰", "").strip()
        m = re.match(r"^(?:(\d{1,2}) )?(?:(\D+?) )?(\d{4}.*)$", core)
        info = INFO_RE.match(m.group(3))
        y = int(info.group("year"))
        if m.group(2):
            rdate = date(y, MONTHS_HI[m.group(2).strip()], int(m.group(1) or 1))
        runtime = int(info.group("h")) * 60 + int(info.group("m") or 0) if info.group("h") else None
        cert = (info.group("cert") or "").strip() or None
        return y, rdate, runtime, cert, expected
    m = INFO_RE.match(line)
    if not m:
        r = re.match(r"^(?:(\d)h)?(?: ?(\d{1,2})m)?$", line)
        if r and (r.group(1) or r.group(2)):  # runtime only, e.g. "1h 58m"
            return None, None, int(r.group(1) or 0) * 60 + int(r.group(2) or 0), None, False
        return None
    y = int(m.group("year"))
    runtime = None
    if m.group("h"):
        runtime = int(m.group("h")) * 60 + int(m.group("m") or 0)
    elif m.group("mo"):
        runtime = int(m.group("mo"))
    cert = (m.group("cert") or "").strip() or None
    return y, rdate, runtime, cert, expected


def parse_regional(path, language):
    lines = [l.rstrip("\n") for l in path.read_text(encoding="utf-8").splitlines()]
    # locate title lines "N. Title" whose number increases monotonically
    starts = []
    expect = 1
    for i, l in enumerate(lines):
        m = TITLE_RE.match(l.strip())
        if m and int(m.group(1)) == expect:
            starts.append(i)
            expect += 1
    movies = []
    for k, i in enumerate(starts):
        end = starts[k + 1] if k + 1 < len(starts) else len(lines)
        block = [l.strip() for l in lines[i:end]]
        title = TITLE_RE.match(block[0]).group(2).strip()
        title = TITLE_FIX.get(title, title)
        body = block[1:]
        # drop the next movie's photo caption (last non-empty line if it mentions " in " or "(year)")
        rec = {"title": title, "language": language, "year": None, "releaseDate": None, "runtimeMin": None,
               "certificate": None, "rating": None, "votes": None, "synopsis": None, "directors": [],
               "cast": [], "budget": None, "boxOffice": None, "verdict": None, "expected": False}
        j = 0
        if j < len(body) and body[j] and not body[j].startswith(("Director", "निर्देशक")):
            r = parse_release(body[j])
            if r:
                rec["year"], rec["releaseDate"], rec["runtimeMin"], rec["certificate"], rec["expected"] = r
                j += 1
        if j < len(body) and RATING_RE.match(body[j]):
            rec["rating"] = float(body[j])
            j += 1
            if j < len(body):
                rec["votes"] = votes_to_int(body[j])
                j += 1
        texts = []
        for l in body[j:]:
            if not l:
                continue
            if l.startswith(("Director", "निर्देशक")):
                rec["directors"], rec["cast"] = parse_credits(l)
            elif l.startswith("Budget -"):
                rec["budget"] = l.split("-", 1)[1].strip()
            elif l.startswith("Box office -"):
                rec["boxOffice"] = l.split("-", 1)[1].strip()
            elif l.startswith("Verdict -"):
                v = l.split("-", 1)[1].strip()
                rec["verdict"] = None if v == "n/a" else v
            else:
                texts.append(l)
        # texts = [synopsis?, caption-of-next-movie?]; the caption always comes last when present
        if texts and k + 1 < len(starts):
            nxt = TITLE_RE.match(lines[starts[k + 1]].strip()).group(2).strip()
            last = texts[-1]
            if nxt in last or re.search(r"\(\d{4}\)$", last) or re.search(r"\bin\b", last) and len(texts) > 1:
                texts = texts[:-1]
        if texts:
            rec["synopsis"] = texts[0]
        if title in SYNOPSIS_EN:
            rec["synopsis"] = SYNOPSIS_EN[title]
        elif rec["synopsis"] and re.search(r"[ऀ-ॿ]", rec["synopsis"]):
            rec["synopsis"] = None
        if title in SKIP_TITLES:
            continue
        movies.append(rec)
    return movies


def parse_top250(path):
    lines = [l.strip() for l in path.read_text(encoding="utf-8").splitlines()]
    out = []
    i = 0
    while i < len(lines):
        if re.match(r"^#\d+$", lines[i]):
            rank = int(lines[i][1:])
            title = lines[i + 1]
            r = parse_release(lines[i + 2])
            rating = float(lines[i + 3]) if RATING_RE.match(lines[i + 3]) else None
            votes = votes_to_int(lines[i + 4])
            y, _, runtime, cert, _ = r
            out.append({"title": title, "language": None, "year": y, "releaseDate": None, "runtimeMin": runtime,
                        "certificate": cert, "rating": rating, "votes": votes, "synopsis": None, "directors": [],
                        "cast": [], "budget": None, "boxOffice": None, "verdict": None, "expected": False,
                        "top250Rank": rank})
            i += 5
        else:
            i += 1
    return out


# Original language of the non-English titles in the Top 250 (everything else there is English)
CLASSIC_LANG = {
    "Seven Samurai": "Japanese", "City of God": "Portuguese", "Life Is Beautiful": "Italian",
    "Spirited Away": "Japanese", "Parasite": "Korean", "Grave of the Fireflies": "Japanese", "Harakiri": "Japanese",
    "Cinema Paradiso": "Italian", "Untouchable": "French", "The Lives of Others": "German",
    "High and Low": "Japanese", "12th Fail": "Hindi", "Your Name.": "Japanese", "3 Idiots": "Hindi",
    "Das Boot": "German", "Princess Mononoke": "Japanese", "Oldboy": "Korean",
    "Attack on Titan: The Last Attack": "Japanese", "Capernaum": "Arabic", "Come and See": "Russian",
    "Incendies": "French", "Ikiru": "Japanese", "The Hunt": "Danish", "Like Stars on Earth": "Hindi",
    "A Separation": "Persian", "M": "German", "Amélie": "French", "Metropolis": "Silent", "Downfall": "German",
    "Dangal": "Hindi", "Bicycle Thieves": "Italian", "Woman of the Dunes": "Japanese", "Ran": "Japanese",
    "Yojimbo": "Japanese", "Demon Slayer: Kimetsu No Yaiba Infinity Castle": "Japanese",
    "The Secret in Their Eyes": "Spanish", "Pan's Labyrinth": "Spanish", "Howl's Moving Castle": "Japanese",
    "The Best of Youth": "Italian", "Rashomon": "Japanese", "Children of Heaven": "Persian",
    "My Neighbour Totoro": "Japanese", "Memories of Murder": "Korean", "Chainsaw Man - The Movie: Reze Arc": "Japanese",
    "Wild Tales": "Spanish", "A Silent Voice: The Movie": "Japanese", "The Wages of Fear": "French",
    "Tokyo Story": "Japanese", "Maharaja": "Tamil", "La haine": "French", "Jai Bhim": "Tamil",
    "Wild Strawberries": "Swedish", "The Seventh Seal": "Swedish", "The Battle of Algiers": "French",
    "My Father and My Son": "Turkish", "The Passion of Joan of Arc": "Silent", "Drishyam": "Hindi",
    "Gangs of Wasseypur": "Hindi", "Autumn Sonata": "Swedish", "City Lights": "Silent", "Modern Times": "Silent",
    "The Kid": "Silent", "The Gold Rush": "Silent", "Sherlock Jr.": "Silent", "The General": "Silent",
}

GENRE_RULES = [
    ("Horror", r"\b(haunt|ghost|spirit|supernatural|demon|curse|terror|possess|sorcer|darkness|horror|evil)"),
    ("Thriller", r"\b(murder|killer|investigat|conspiracy|thriller|heist|kidnap|hostage|mystery|vanish|missing|psycho)"),
    ("Action", r"\b(action|underworld|gang|assassin|revenge|vengeance|smuggl|fight|battle|war\b|soldier|army|cop\b|police|spy|mission|warrior|criminal|exile)"),
    ("Romance", r"\b(love\b|in love|romance|romantic|crush|blind date|marriage|wedding|newlywed)"),
    ("Comedy", r"\b(comedy|hilarious|quirky|laughter|humor|humorous|riotous|wacky|chaos|chaotic|funny|misfits)"),
    ("Family", r"\b(family|father|mother|son|daughter|sister|brother|kids|children)"),
    ("Sports", r"\b(athlete|race|racing|wrestl|cricket|shooter|motocross|boat race)"),
    ("Historical", r"\b(1971|1965|partition|1980s|1970s|17th century|biograph|life of|based on true|real-life|independence)"),
    ("Mythology", r"\b(epic|krishna|mahabharata|ramayan|goddess|divine|lord|immortal)"),
]


def guess_genres(m):
    text = (m.get("synopsis") or "").lower()
    found = [g for g, rx in GENRE_RULES if re.search(rx, text)]
    if not found and text:
        found = ["Drama"]
    return found[:3]


def status_of(m):
    if m.get("top250Rank") and m["year"] and m["year"] < 2026:
        return "CLASSIC"
    if m["releaseDate"]:
        return "NOW_SHOWING" if m["releaseDate"] <= TODAY else "COMING_SOON"
    if m["expected"] or m["year"] is None or m["year"] > TODAY.year:
        return "COMING_SOON"
    if m["rating"] is not None or m["runtimeMin"]:
        return "NOW_SHOWING"
    return "COMING_SOON"


def main():
    all_movies = []
    for fname, lang in LISTS:
        all_movies += parse_regional(DATA / fname, lang)
    all_movies += parse_top250(DATA / TOP250)

    merged = {}
    for m in all_movies:
        key = (m["title"].lower(), m["year"])
        if key in merged:
            prev = merged[key]
            langs = prev["languages"]
            if m["language"] and m["language"] not in langs:
                langs.append(m["language"])
            for f in ("synopsis", "runtimeMin", "certificate", "rating", "votes"):
                prev[f] = prev[f] or m[f]
            continue
        m["languages"] = [m["language"]] if m["language"] else []
        del m["language"]
        merged[key] = m

    result = []
    for m in merged.values():
        m["status"] = status_of(m)
        if not m["languages"]:
            m["languages"] = [CLASSIC_LANG.get(m["title"], "English")]
        m["genres"] = guess_genres(m)
        if isinstance(m["releaseDate"], date):
            m["releaseDate"] = m["releaseDate"].isoformat()
        result.append(m)
    (OUT_DIR / "movies.json").write_text(json.dumps(result, ensure_ascii=False, indent=1), encoding="utf-8")
    from collections import Counter
    print(len(result), Counter(m["status"] for m in result))
    print(Counter(l for m in result for l in m["languages"]))


if __name__ == "__main__":
    main()

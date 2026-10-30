// Browser-side gateway SDK loaders. Only public identifiers (publishable key, key id, order id,
// client secret) ever reach this code; secret keys stay on the Spring Boot server.

const loaded = new Map<string, Promise<void>>();

export function loadScript(src: string): Promise<void> {
  if (!loaded.has(src)) {
    loaded.set(src, new Promise((resolve, reject) => {
      const s = document.createElement("script");
      s.src = src;
      s.async = true;
      s.onload = () => resolve();
      s.onerror = () => { loaded.delete(src); reject(new Error(`Could not load ${src}`)); };
      document.head.appendChild(s);
    }));
  }
  return loaded.get(src)!;
}

type RazorpayResponse = { razorpay_payment_id: string; razorpay_order_id: string; razorpay_signature: string };
type RazorpayCtor = new (opts: Record<string, unknown>) => { open: () => void; on: (ev: string, cb: (r: unknown) => void) => void };

export async function openRazorpay(opts: {
  keyId: string; orderId: string; amountPaise: number; name: string; description: string; prefill: { name?: string; email?: string; contact?: string };
  method?: string; onSuccess: (r: RazorpayResponse) => void; onDismiss: () => void;
}) {
  await loadScript("https://checkout.razorpay.com/v1/checkout.js");
  const Razorpay = (window as unknown as { Razorpay: RazorpayCtor }).Razorpay;
  const rzp = new Razorpay({
    key: opts.keyId,
    order_id: opts.orderId,
    amount: opts.amountPaise,
    currency: "INR",
    name: opts.name,
    description: opts.description,
    prefill: opts.prefill,
    theme: { color: "#ea580c" },
    // Nudge Checkout to the method the customer picked (UPI covers Google Pay / PhonePe apps).
    ...(opts.method === "UPI" || opts.method === "GPAY" || opts.method === "PHONEPE" ? { method: "upi" } : {}),
    handler: opts.onSuccess,
    modal: { ondismiss: opts.onDismiss },
  });
  rzp.open();
}

type StripeLike = {
  elements: (o: Record<string, unknown>) => { create: (t: string, o?: Record<string, unknown>) => { mount: (el: HTMLElement) => void; destroy: () => void } };
  confirmPayment: (o: Record<string, unknown>) => Promise<{ error?: { message?: string } }>;
};

export async function mountStripe(publishableKey: string, clientSecret: string, el: HTMLElement) {
  await loadScript("https://js.stripe.com/v3/");
  const stripe = (window as unknown as { Stripe: (k: string) => StripeLike }).Stripe(publishableKey);
  const elements = stripe.elements({ clientSecret, appearance: { theme: "stripe", variables: { colorPrimary: "#ea580c", borderRadius: "12px" } } });
  const payment = elements.create("payment", { layout: "tabs" });
  payment.mount(el);
  return {
    confirm: (returnUrl: string) => stripe.confirmPayment({ elements, confirmParams: { return_url: returnUrl } }),
    destroy: () => payment.destroy(),
  };
}

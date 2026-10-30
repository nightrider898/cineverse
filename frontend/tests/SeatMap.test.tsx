import { render, screen, fireEvent } from "@testing-library/react";
import { SeatMap } from "@/components/booking/SeatMap";
import type { Seat } from "@/types/api";

const seat = (label: string, col: number, status: Seat["status"], type = "GOLD"): Seat => ({
  id: col, label, row: label[0], number: Number(label.slice(1)), rowIndex: 1, colIndex: col, seatType: type, price: 250, status, lockedByYou: false,
});

describe("SeatMap", () => {
  const seats = [seat("A1", 1, "AVAILABLE"), seat("A2", 2, "BOOKED"), seat("A3", 4, "LOCKED"), seat("A4", 5, "DISABLED")];

  it("gives every seat an accessible label with type and state", () => {
    render(<SeatMap seats={seats} columns={5} pricing={[{ seatType: "GOLD", label: "Gold", price: 250 }]} selected={new Set()} onToggle={() => {}} />);
    expect(screen.getByRole("button", { name: /Seat A1, Gold, available/ })).toBeEnabled();
    expect(screen.getByRole("button", { name: /Seat A2, Gold, sold/ })).toBeDisabled();
    expect(screen.getByRole("button", { name: /Seat A3, Gold, held by another customer/ })).toBeDisabled();
    expect(screen.getByLabelText(/Seat A4, not available/)).toBeInTheDocument();
  });

  it("only lets available seats be toggled", () => {
    const onToggle = vi.fn();
    render(<SeatMap seats={seats} columns={5} pricing={[]} selected={new Set(["A1"])} onToggle={onToggle} />);
    const a1 = screen.getByRole("button", { name: /Seat A1/ });
    expect(a1).toHaveAttribute("aria-pressed", "true");
    fireEvent.click(a1);
    fireEvent.click(screen.getByRole("button", { name: /Seat A2/ }));
    expect(onToggle).toHaveBeenCalledTimes(1);
  });
});

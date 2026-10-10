import { describe, expect, it } from "vitest";
import { addDays, daysBetween, fileSize, label, period, plate, rwf, rwfCompact, shortId, specs } from "../format";
import { matches } from "../admin/AdminShell";
import type { Vehicle } from "../api";

describe("formatting", () => {
  it("shows plates the Rwandan way", () => {
    expect(plate("RAB123A")).toBe("RAB 123 A");
    expect(plate("odd")).toBe("odd");
  });

  it("formats money in Rwandan francs", () => {
    expect(rwf(425000)).toBe("RWF 425,000");
    expect(rwfCompact(18_400_000)).toBe("RWF 18.4M");
    expect(rwfCompact(2_000_000)).toBe("RWF 2M");
    expect(rwfCompact(425_000)).toBe("RWF 425K");
  });

  it("formats rental periods compactly", () => {
    expect(period("2025-06-12", "2025-06-17")).toBe("12–17 Jun 2025");
    expect(period("2025-06-28", "2025-07-03")).toBe("28 Jun – 3 Jul 2025");
  });

  it("does date arithmetic on calendar days", () => {
    expect(addDays("2025-06-30", 1)).toBe("2025-07-01");
    expect(daysBetween("2025-06-12", "2025-06-17")).toBe(5);
  });

  it("derives short readable ids from UUIDs", () => {
    expect(shortId("CTR", "ab12cd34-0000-0000-0000-000000000000")).toBe("CTR-AB12");
  });

  it("labels enums and vehicle specs", () => {
    expect(label("SEDAN")).toBe("Sedan");
    expect(label("SUV")).toBe("SUV");
    expect(label(null)).toBe("—");
    const v = { transmission: "AUTOMATIC", seats: 5, fuelType: "PETROL" } as Vehicle;
    expect(specs(v)).toBe("Automatic · 5 seats · Petrol");
    expect(fileSize(2.5 * 1024 * 1024)).toBe("2.5 MB");
  });
});

describe("table search", () => {
  it("ignores case and spaces so 'rab 123' finds RAB123A", () => {
    expect(matches("rab 123", "Toyota RAV4", "RAB123A")).toBe(true);
    expect(matches("prado", "Toyota RAV4", "RAB123A")).toBe(false);
    expect(matches("", "anything")).toBe(true);
  });
});

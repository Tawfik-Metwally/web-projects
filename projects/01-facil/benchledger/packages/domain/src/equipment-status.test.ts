import { describe, expect, it } from "vitest";

import { deriveEquipmentStatus } from "./equipment-status.js";

const asOf = new Date("2026-08-15T12:00:00.000Z");

describe("deriveEquipmentStatus", () => {
  it("gives logical deletion precedence over operational states", () => {
    expect(
      deriveEquipmentStatus({
        asOf,
        isDeleted: true,
        isOutOfService: true,
        calibrationIntervalDays: 365,
        lastSuccessfulCalibrationAt: null,
      }),
    ).toEqual({ status: "DELETED", dueDate: null });
  });

  it("gives an explicit removal from service the highest precedence", () => {
    expect(
      deriveEquipmentStatus({
        asOf,
        isOutOfService: true,
        calibrationIntervalDays: 365,
        lastSuccessfulCalibrationAt: new Date("2026-08-01T00:00:00.000Z"),
      }),
    ).toEqual({ status: "OUT_OF_SERVICE", dueDate: null });
  });

  it("keeps equipment without a calibration requirement active", () => {
    expect(
      deriveEquipmentStatus({
        asOf,
        isOutOfService: false,
        calibrationIntervalDays: null,
        lastSuccessfulCalibrationAt: null,
      }),
    ).toEqual({ status: "ACTIVE", dueDate: null });
  });

  it("surfaces the latest failed calibration ahead of deadline states", () => {
    expect(
      deriveEquipmentStatus({
        asOf,
        isOutOfService: false,
        calibrationIntervalDays: 365,
        lastSuccessfulCalibrationAt: new Date("2026-07-01T00:00:00.000Z"),
        lastFailedCalibrationAt: new Date("2026-08-14T16:30:00.000Z"),
      }),
    ).toEqual({ status: "CALIBRATION_FAILED", dueDate: "2027-07-01" });
  });

  it("marks required calibration with no successful event as overdue", () => {
    expect(
      deriveEquipmentStatus({
        asOf,
        isOutOfService: false,
        calibrationIntervalDays: 365,
        lastSuccessfulCalibrationAt: null,
      }),
    ).toEqual({ status: "OVERDUE", dueDate: null });
  });

  it("marks an upcoming deadline within the warning window as due soon", () => {
    expect(
      deriveEquipmentStatus({
        asOf,
        isOutOfService: false,
        calibrationIntervalDays: 365,
        lastSuccessfulCalibrationAt: new Date("2025-09-01T18:00:00.000Z"),
      }),
    ).toEqual({ status: "DUE_SOON", dueDate: "2026-09-01" });
  });

  it("becomes overdue on the UTC day after the due date", () => {
    expect(
      deriveEquipmentStatus({
        asOf,
        isOutOfService: false,
        calibrationIntervalDays: 365,
        lastSuccessfulCalibrationAt: new Date("2025-08-14T23:59:00.000Z"),
      }),
    ).toEqual({ status: "OVERDUE", dueDate: "2026-08-14" });
  });
});

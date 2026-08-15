export type EquipmentStatus = "ACTIVE" | "DUE_SOON" | "OVERDUE" | "OUT_OF_SERVICE";

export interface EquipmentStatusInput {
  asOf: Date;
  isOutOfService: boolean;
  calibrationIntervalDays: number | null;
  lastSuccessfulCalibrationAt: Date | null;
  warningWindowDays?: number;
}

export interface EquipmentStatusResult {
  status: EquipmentStatus;
  dueDate: string | null;
}

const millisecondsPerDay = 86_400_000;

function startOfUtcDay(value: Date): Date {
  return new Date(Date.UTC(value.getUTCFullYear(), value.getUTCMonth(), value.getUTCDate()));
}

function addUtcDays(value: Date, days: number): Date {
  return new Date(startOfUtcDay(value).getTime() + days * millisecondsPerDay);
}

function toUtcDate(value: Date): string {
  return value.toISOString().slice(0, 10);
}

export function deriveEquipmentStatus(input: EquipmentStatusInput): EquipmentStatusResult {
  if (input.isOutOfService) {
    return { status: "OUT_OF_SERVICE", dueDate: null };
  }

  if (input.calibrationIntervalDays === null) {
    return { status: "ACTIVE", dueDate: null };
  }

  if (!Number.isInteger(input.calibrationIntervalDays) || input.calibrationIntervalDays <= 0) {
    throw new RangeError("calibrationIntervalDays must be a positive integer or null");
  }

  if (!input.lastSuccessfulCalibrationAt) {
    return { status: "OVERDUE", dueDate: null };
  }

  const warningWindowDays = input.warningWindowDays ?? 30;
  if (!Number.isInteger(warningWindowDays) || warningWindowDays < 0) {
    throw new RangeError("warningWindowDays must be a non-negative integer");
  }

  const today = startOfUtcDay(input.asOf);
  const dueDate = addUtcDays(input.lastSuccessfulCalibrationAt, input.calibrationIntervalDays);
  const warningDate = addUtcDays(today, warningWindowDays);

  if (dueDate < today) {
    return { status: "OVERDUE", dueDate: toUtcDate(dueDate) };
  }

  if (dueDate <= warningDate) {
    return { status: "DUE_SOON", dueDate: toUtcDate(dueDate) };
  }

  return { status: "ACTIVE", dueDate: toUtcDate(dueDate) };
}

import { BadRequestException } from "@nestjs/common";

export type InputRecord = Record<string, unknown>;

export function requireInputRecord(value: unknown): InputRecord {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new BadRequestException("request body must be an object");
  }
  return value as InputRecord;
}

export function rejectUnknownFields(input: InputRecord, allowedFields: ReadonlySet<string>): void {
  const unknownFields = Object.keys(input).filter((field) => !allowedFields.has(field));
  if (unknownFields.length > 0) {
    throw new BadRequestException(`unknown fields: ${unknownFields.join(", ")}`);
  }
}

export function requireTrimmedText(value: unknown, field: string, maxLength: number): string {
  if (typeof value !== "string" || value.trim().length === 0 || value.trim().length > maxLength) {
    throw new BadRequestException(`${field} must have between 1 and ${maxLength} characters`);
  }
  return value.trim();
}

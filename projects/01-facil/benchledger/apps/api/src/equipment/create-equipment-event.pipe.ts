import { BadRequestException, Injectable, type PipeTransform } from "@nestjs/common";

export interface CreateEquipmentEventInput {
  type: "CALIBRATION" | "MAINTENANCE";
  occurredAt: Date;
  successful?: boolean;
  note?: string;
}

type InputRecord = Record<string, unknown>;

@Injectable()
export class CreateEquipmentEventPipe implements PipeTransform<unknown, CreateEquipmentEventInput> {
  public transform(value: unknown): CreateEquipmentEventInput {
    if (typeof value !== "object" || value === null || Array.isArray(value)) {
      throw new BadRequestException("request body must be an object");
    }

    const input = value as InputRecord;
    const unknownFields = Object.keys(input).filter((field) => !["type", "occurredAt", "successful", "note"].includes(field));
    if (unknownFields.length > 0) {
      throw new BadRequestException(`unknown fields: ${unknownFields.join(", ")}`);
    }

    if (input.type !== "CALIBRATION" && input.type !== "MAINTENANCE") {
      throw new BadRequestException("type must be CALIBRATION or MAINTENANCE");
    }

    if (typeof input.occurredAt !== "string") {
      throw new BadRequestException("occurredAt must be an ISO date-time string");
    }
    const occurredAt = new Date(input.occurredAt);
    if (Number.isNaN(occurredAt.getTime())) {
      throw new BadRequestException("occurredAt must be a valid ISO date-time string");
    }

    if (input.successful !== undefined && typeof input.successful !== "boolean") {
      throw new BadRequestException("successful must be a boolean");
    }
    if (input.type === "CALIBRATION" && typeof input.successful !== "boolean") {
      throw new BadRequestException("successful is required for calibration events");
    }

    let note: string | undefined;
    if (input.note !== undefined && input.note !== null && input.note !== "") {
      if (typeof input.note !== "string" || input.note.trim().length > 500) {
        throw new BadRequestException("note must have at most 500 characters");
      }
      note = input.note.trim();
    }

    return { type: input.type, occurredAt, successful: input.successful as boolean | undefined, note };
  }
}

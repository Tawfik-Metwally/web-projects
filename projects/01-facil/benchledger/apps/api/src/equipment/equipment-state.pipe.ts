import { BadRequestException, Injectable, type PipeTransform } from "@nestjs/common";

export interface EquipmentStateInput {
  note?: string;
}

@Injectable()
export class EquipmentStatePipe implements PipeTransform<unknown, EquipmentStateInput> {
  public transform(value: unknown): EquipmentStateInput {
    if (value === undefined || value === null) return {};
    if (typeof value !== "object" || Array.isArray(value)) {
      throw new BadRequestException("request body must be an object");
    }

    const input = value as Record<string, unknown>;
    const unknownFields = Object.keys(input).filter((field) => field !== "note");
    if (unknownFields.length > 0) {
      throw new BadRequestException(`unknown fields: ${unknownFields.join(", ")}`);
    }

    if (input.note === undefined || input.note === null || input.note === "") return {};
    if (typeof input.note !== "string" || input.note.trim().length > 500) {
      throw new BadRequestException("note must have at most 500 characters");
    }

    return { note: input.note.trim() };
  }
}

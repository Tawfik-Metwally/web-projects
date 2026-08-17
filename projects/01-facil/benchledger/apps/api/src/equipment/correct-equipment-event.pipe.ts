import { BadRequestException, Injectable, type PipeTransform } from "@nestjs/common";

export interface CorrectEquipmentEventInput {
  note: string;
}

@Injectable()
export class CorrectEquipmentEventPipe implements PipeTransform<unknown, CorrectEquipmentEventInput> {
  public transform(value: unknown): CorrectEquipmentEventInput {
    if (typeof value !== "object" || value === null || Array.isArray(value)) {
      throw new BadRequestException("request body must be an object");
    }

    const input = value as Record<string, unknown>;
    const unknownFields = Object.keys(input).filter((field) => field !== "note");
    if (unknownFields.length > 0) {
      throw new BadRequestException(`unknown fields: ${unknownFields.join(", ")}`);
    }
    if (typeof input.note !== "string" || input.note.trim().length === 0 || input.note.trim().length > 500) {
      throw new BadRequestException("note must have between 1 and 500 characters");
    }
    return { note: input.note.trim() };
  }
}

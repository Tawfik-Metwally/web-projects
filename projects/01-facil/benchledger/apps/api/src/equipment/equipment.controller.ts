import { Body, Controller, Delete, Get, Inject, Param, ParseUUIDPipe, Patch, Post } from "@nestjs/common";

import { CreateEquipmentPipe, type CreateEquipmentInput } from "./create-equipment.pipe.js";
import { CorrectEquipmentEventPipe, type CorrectEquipmentEventInput } from "./correct-equipment-event.pipe.js";
import { CreateEquipmentEventPipe, type CreateEquipmentEventInput } from "./create-equipment-event.pipe.js";
import { EquipmentStatePipe, type EquipmentStateInput } from "./equipment-state.pipe.js";
import { EquipmentService } from "./equipment.service.js";
import { UpdateEquipmentPipe, type UpdateEquipmentInput } from "./update-equipment.pipe.js";

@Controller("equipment")
export class EquipmentController {
  public constructor(@Inject(EquipmentService) private readonly equipmentService: EquipmentService) {}

  @Post()
  public create(@Body(CreateEquipmentPipe) input: CreateEquipmentInput) {
    return this.equipmentService.create(input);
  }

  @Get()
  public findAll() {
    return this.equipmentService.findAll();
  }

  @Get(":id")
  public findOne(@Param("id", new ParseUUIDPipe({ version: "4" })) id: string) {
    return this.equipmentService.findOne(id);
  }

  @Patch(":id")
  public update(
    @Param("id", new ParseUUIDPipe({ version: "4" })) id: string,
    @Body(UpdateEquipmentPipe) input: UpdateEquipmentInput,
  ) {
    return this.equipmentService.update(id, input);
  }

  @Delete(":id")
  public remove(
    @Param("id", new ParseUUIDPipe({ version: "4" })) id: string,
    @Body(EquipmentStatePipe) input: EquipmentStateInput,
  ) {
    return this.equipmentService.remove(id, input.note);
  }

  @Post(":id/restore-deleted")
  public restoreDeleted(
    @Param("id", new ParseUUIDPipe({ version: "4" })) id: string,
    @Body(EquipmentStatePipe) input: EquipmentStateInput,
  ) {
    return this.equipmentService.restoreDeleted(id, input.note);
  }

  @Post(":id/events")
  public addEvent(
    @Param("id", new ParseUUIDPipe({ version: "4" })) id: string,
    @Body(CreateEquipmentEventPipe) input: CreateEquipmentEventInput,
  ) {
    return this.equipmentService.addEvent(id, input);
  }

  @Post(":id/events/:eventId/corrections")
  public correctEvent(
    @Param("id", new ParseUUIDPipe({ version: "4" })) id: string,
    @Param("eventId", new ParseUUIDPipe({ version: "4" })) eventId: string,
    @Body(CorrectEquipmentEventPipe) input: CorrectEquipmentEventInput,
  ) {
    return this.equipmentService.correctEvent(id, eventId, input.note);
  }

  @Post(":id/archive")
  public archive(
    @Param("id", new ParseUUIDPipe({ version: "4" })) id: string,
    @Body(EquipmentStatePipe) input: EquipmentStateInput,
  ) {
    return this.equipmentService.archive(id, input.note);
  }

  @Post(":id/restore")
  public restore(
    @Param("id", new ParseUUIDPipe({ version: "4" })) id: string,
    @Body(EquipmentStatePipe) input: EquipmentStateInput,
  ) {
    return this.equipmentService.restore(id, input.note);
  }
}

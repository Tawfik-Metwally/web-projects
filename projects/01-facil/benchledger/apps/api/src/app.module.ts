import { Module } from "@nestjs/common";

import { DatabaseModule } from "./database/database.module.js";
import { EquipmentModule } from "./equipment/equipment.module.js";
import { HealthController } from "./health/health.controller.js";
import { HealthService } from "./health/health.service.js";

@Module({
  imports: [DatabaseModule, EquipmentModule],
  controllers: [HealthController],
  providers: [HealthService],
})
export class AppModule {}

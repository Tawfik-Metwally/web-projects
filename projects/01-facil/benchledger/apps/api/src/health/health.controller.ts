import { Controller, Get, Inject } from "@nestjs/common";

import { HealthService, type HealthResponse } from "./health.service.js";

@Controller("health")
export class HealthController {
  public constructor(@Inject(HealthService) private readonly healthService: HealthService) {}

  @Get()
  public check(): Promise<HealthResponse> {
    return this.healthService.check();
  }
}

import { Inject, Injectable } from "@nestjs/common";

import { PrismaService } from "../database/prisma.service.js";

export interface HealthResponse {
  status: "ok";
  database: "reachable";
}

@Injectable()
export class HealthService {
  public constructor(@Inject(PrismaService) private readonly prisma: PrismaService) {}

  public async check(): Promise<HealthResponse> {
    await this.prisma.$queryRaw`SELECT 1`;

    return {
      status: "ok",
      database: "reachable",
    };
  }
}

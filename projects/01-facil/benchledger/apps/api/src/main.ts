import "reflect-metadata";

import { NestFactory } from "@nestjs/core";

import { AppModule } from "./app.module.js";

const port = Number(process.env.API_PORT ?? 3000);
const app = await NestFactory.create(AppModule);

app.setGlobalPrefix("api");
app.enableShutdownHooks();
const httpAdapter = app.getHttpAdapter().getInstance() as { disable(name: string): void };
httpAdapter.disable("x-powered-by");
app.enableCors({
  origin: process.env.WEB_ORIGIN ?? "http://localhost:5173",
});

await app.listen(port, "0.0.0.0");

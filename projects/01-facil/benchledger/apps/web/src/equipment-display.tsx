import { Chip, Tooltip } from "@mui/material";

import type { EquipmentEvent, EquipmentStatus } from "./equipment.js";

export const statusMeta: Record<EquipmentStatus, { label: string; color: "success" | "warning" | "error" | "default"; description: string }> = {
  ACTIVE: { label: "Active", color: "success", description: "Available for use and not inside the calibration warning window." },
  DUE_SOON: { label: "Due soon", color: "warning", description: "Calibration is due within 30 days." },
  OVERDUE: { label: "Overdue", color: "error", description: "Calibration is required and no current successful calibration exists." },
  CALIBRATION_FAILED: { label: "Calibration failed", color: "error", description: "The latest effective calibration failed; use should be restricted until it passes." },
  OUT_OF_SERVICE: { label: "Out of service", color: "default", description: "The equipment was explicitly removed from operational use." },
  DELETED: { label: "Deleted", color: "default", description: "Removed from the operational inventory with its history preserved." },
};

export const eventLabels: Record<EquipmentEvent["type"], string> = {
  CALIBRATION: "Calibration",
  MAINTENANCE: "Maintenance",
  OUT_OF_SERVICE: "Removed from service",
  RETURNED_TO_SERVICE: "Returned to service",
  CORRECTION: "Correction recorded",
  DELETED: "Deleted from inventory",
  RESTORED_FROM_DELETION: "Restored to inventory",
};

export function formatDate(value: string | null): string {
  if (!value) return "Not available";
  const dateOnly = /^\d{4}-\d{2}-\d{2}$/.test(value);
  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    ...(dateOnly ? { timeZone: "UTC" } : {}),
  }).format(new Date(value));
}

export function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function StatusChip({ status }: { status: EquipmentStatus }) {
  const meta = statusMeta[status];
  return <Tooltip title={meta.description}><Chip label={meta.label} color={meta.color} size="small" variant={status === "OUT_OF_SERVICE" || status === "DELETED" ? "outlined" : "filled"} /></Tooltip>;
}

export interface Equipment {
  id: string;
  assetTag: string;
  name: string;
  category: string;
  manufacturer: string | null;
  model: string | null;
  serialNumber: string | null;
  location: string;
  calibrationIntervalDays: number | null;
  archivedAt: string | null;
  deletedAt: string | null;
  createdAt: string;
  updatedAt: string;
  status: EquipmentStatus;
  dueDate: string | null;
  lastSuccessfulCalibrationAt: string | null;
  events: EquipmentEvent[];
}

export type EquipmentStatus = "ACTIVE" | "DUE_SOON" | "OVERDUE" | "CALIBRATION_FAILED" | "OUT_OF_SERVICE" | "DELETED";
export type EquipmentEventType = "CALIBRATION" | "MAINTENANCE" | "OUT_OF_SERVICE" | "RETURNED_TO_SERVICE" | "CORRECTION" | "DELETED" | "RESTORED_FROM_DELETION";

export interface EquipmentEvent {
  id: string;
  type: EquipmentEventType;
  occurredAt: string;
  successful: boolean | null;
  note: string | null;
  correctsEventId: string | null;
  createdAt: string;
}

export interface CreateEquipmentInput {
  assetTag: string;
  name: string;
  category: string;
  manufacturer?: string;
  model?: string;
  serialNumber?: string;
  location: string;
  calibrationIntervalDays?: number;
}

export interface UpdateEquipmentInput {
  name?: string;
  category?: string;
  manufacturer?: string | null;
  model?: string | null;
  serialNumber?: string | null;
  location?: string;
  calibrationIntervalDays?: number | null;
}

export interface CreateEquipmentEventInput {
  type: "CALIBRATION" | "MAINTENANCE";
  occurredAt: string;
  successful?: boolean;
  note?: string;
}

const apiUrl = import.meta.env.VITE_API_URL ?? "http://localhost:3000/api";

async function parseError(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { message?: string | string[] };
    return Array.isArray(body.message) ? body.message.join(", ") : body.message ?? "Request failed";
  } catch {
    return `Request failed with status ${response.status}`;
  }
}

export async function listEquipment(signal?: AbortSignal): Promise<Equipment[]> {
  const response = await fetch(`${apiUrl}/equipment`, { signal });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }

  return (await response.json()) as Equipment[];
}

export async function createEquipment(input: CreateEquipmentInput): Promise<Equipment> {
  const response = await fetch(`${apiUrl}/equipment`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });

  if (!response.ok) {
    throw new Error(await parseError(response));
  }

  return (await response.json()) as Equipment;
}

async function mutateEquipment(path: string, method: "PATCH" | "POST" | "DELETE", body: object): Promise<Equipment> {
  const response = await fetch(`${apiUrl}/equipment/${path}`, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) throw new Error(await parseError(response));
  return (await response.json()) as Equipment;
}

export function updateEquipment(id: string, input: UpdateEquipmentInput): Promise<Equipment> {
  return mutateEquipment(id, "PATCH", input);
}

export function deleteEquipment(id: string, note?: string): Promise<Equipment> {
  return mutateEquipment(id, "DELETE", { note });
}

export function restoreDeletedEquipment(id: string, note?: string): Promise<Equipment> {
  return mutateEquipment(`${id}/restore-deleted`, "POST", { note });
}

export function addEquipmentEvent(id: string, input: CreateEquipmentEventInput): Promise<Equipment> {
  return mutateEquipment(`${id}/events`, "POST", input);
}

export function archiveEquipment(id: string, note?: string): Promise<Equipment> {
  return mutateEquipment(`${id}/archive`, "POST", { note });
}

export function restoreEquipment(id: string, note?: string): Promise<Equipment> {
  return mutateEquipment(`${id}/restore`, "POST", { note });
}

export function correctEquipmentEvent(id: string, eventId: string, note: string): Promise<Equipment> {
  return mutateEquipment(`${id}/events/${eventId}/corrections`, "POST", { note });
}

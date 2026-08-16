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
  createdAt: string;
  updatedAt: string;
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

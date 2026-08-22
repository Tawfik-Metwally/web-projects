import { lazy, Suspense, useEffect, useMemo, useState } from "react";
import {
  AddOutlined,
  CheckCircleOutlined,
  DeleteOutlineOutlined,
  Inventory2Outlined,
  RefreshOutlined,
  SearchOutlined,
  WarningAmberOutlined,
} from "@mui/icons-material";
import {
  Alert,
  AppBar,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  FormControl,
  IconButton,
  InputAdornment,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Snackbar,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Toolbar,
  Tooltip,
  Typography,
} from "@mui/material";

import { listEquipment, type Equipment, type EquipmentEvent, type EquipmentStatus } from "./equipment.js";
import { formatDate, formatDateTime, statusMeta, StatusChip } from "./equipment-display.js";

interface HealthResponse {
  status: "ok";
  database: "reachable";
}

type ConnectionState = "checking" | "ready" | "unavailable";
type StatusFilter = "ALL" | EquipmentStatus;
type ActiveDialog =
  | { type: "form"; mode: "create" | "edit" }
  | { type: "event" | "state" | "delete" | "restoreDeleted" }
  | { type: "correction"; event: EquipmentEvent }
  | null;

const apiUrl = import.meta.env.VITE_API_URL ?? "http://localhost:3000/api";
const loadDialogs = () => import("./equipment-dialogs.js");
const EquipmentFormDialog = lazy(async () => ({ default: (await loadDialogs()).EquipmentFormDialog }));
const EventDialog = lazy(async () => ({ default: (await loadDialogs()).EventDialog }));
const StateDialog = lazy(async () => ({ default: (await loadDialogs()).StateDialog }));
const CorrectionDialog = lazy(async () => ({ default: (await loadDialogs()).CorrectionDialog }));
const DeleteEquipmentDialog = lazy(async () => ({ default: (await loadDialogs()).DeleteEquipmentDialog }));
const RestoreDeletedDialog = lazy(async () => ({ default: (await loadDialogs()).RestoreDeletedDialog }));
const EquipmentDetailsDrawer = lazy(async () => ({
  default: (await import("./equipment-details-drawer.js")).EquipmentDetailsDrawer,
}));

function Metric({ label, value, tone }: { label: string; value: number; tone?: string }) {
  return (
    <Paper variant="outlined" sx={{ p: 1.75, minWidth: 0, borderTop: `3px solid ${tone ?? "#17324d"}` }}>
      <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.05em" }}>
        {label}
      </Typography>
      <Typography sx={{ mt: 0.25, fontSize: "1.75rem", lineHeight: 1.1, fontWeight: 750 }}>{value}</Typography>
    </Paper>
  );
}

export function App() {
  const [connection, setConnection] = useState<ConnectionState>("checking");
  const [equipment, setEquipment] = useState<Equipment[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [activeDialog, setActiveDialog] = useState<ActiveDialog>(null);
  const [showDeleted, setShowDeleted] = useState(false);
  const [notice, setNotice] = useState<{ severity: "success" | "error"; text: string } | null>(null);

  const selected = equipment.find((item) => item.id === selectedId);

  async function load() {
    setLoading(true);
    try {
      const [healthResponse, equipmentList] = await Promise.all([fetch(`${apiUrl}/health`), listEquipment()]);
      if (!healthResponse.ok) throw new Error(`Health check failed with status ${healthResponse.status}`);
      const health = (await healthResponse.json()) as HealthResponse;
      setConnection(health.status === "ok" ? "ready" : "unavailable");
      setEquipment(equipmentList);
    } catch {
      setConnection("unavailable");
      setNotice({ severity: "error", text: "Could not connect to the BenchLedger services." });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  function acceptSaved(saved: Equipment, text: string) {
    setEquipment((current) => {
      const exists = current.some((item) => item.id === saved.id);
      return exists ? current.map((item) => (item.id === saved.id ? saved : item)) : [saved, ...current];
    });
    setSelectedId(saved.id);
    setNotice({ severity: "success", text });
  }

  const filteredEquipment = useMemo(() => {
    const normalized = search.trim().toLowerCase();
    return equipment.filter((item) => {
      const matchesView = showDeleted ? item.deletedAt !== null : item.deletedAt === null;
      const matchesStatus = statusFilter === "ALL" || item.status === statusFilter;
      const matchesSearch = !normalized || [item.assetTag, item.name, item.category, item.location, item.serialNumber ?? ""]
        .some((value) => value.toLowerCase().includes(normalized));
      return matchesView && matchesStatus && matchesSearch;
    });
  }, [equipment, search, showDeleted, statusFilter]);

  const counts = useMemo(() => {
    const inventory = equipment.filter((item) => item.deletedAt === null);
    return {
      total: inventory.length,
      deleted: equipment.length - inventory.length,
      active: inventory.filter((item) => item.status === "ACTIVE").length,
      attention: inventory.filter((item) => item.status === "DUE_SOON").length,
      overdue: inventory.filter((item) => item.status === "OVERDUE").length,
      failed: inventory.filter((item) => item.status === "CALIBRATION_FAILED").length,
      outOfService: inventory.filter((item) => item.status === "OUT_OF_SERVICE").length,
    };
  }, [equipment]);

  function changeDeletedView(next: boolean) {
    setShowDeleted(next);
    setStatusFilter("ALL");
    setSearch("");
    setSelectedId(null);
  }

  return (
    <Box sx={{ minHeight: "100vh" }}>
      <AppBar position="static" color="primary" elevation={0}>
        <Toolbar sx={{ minHeight: 58 }}>
          <Inventory2Outlined sx={{ mr: 1.25 }} />
          <Box sx={{ flexGrow: 1 }}>
            <Typography variant="h1" color="inherit">BenchLedger</Typography>
            <Typography variant="caption" sx={{ color: "rgba(255,255,255,.72)", display: { xs: "none", sm: "block" } }}>Laboratory equipment operations</Typography>
          </Box>
          <Chip
            size="small"
            icon={connection === "ready" ? <CheckCircleOutlined /> : connection === "checking" ? <CircularProgress size={14} /> : <WarningAmberOutlined />}
            label={connection === "ready" ? "Services online" : connection === "checking" ? "Connecting" : "Services unavailable"}
            sx={{ bgcolor: connection === "ready" ? "#d7f0e2" : "#fff1d6", color: "#17324d", "& .MuiChip-icon": { color: "inherit" } }}
          />
        </Toolbar>
      </AppBar>

      <Box component="main" sx={{ width: "min(1440px, calc(100% - 32px))", mx: "auto", py: 3 }}>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ justifyContent: "space-between", alignItems: { xs: "stretch", sm: "center" }, mb: 2.5 }}>
          <Box>
            <Typography variant="h2">{showDeleted ? "Deleted records" : "Equipment inventory"}</Typography>
            <Typography variant="body2" color="text.secondary">
              {showDeleted ? "Equipment removed from operations, with its complete history preserved." : "Operational state, calibration exposure and technical history."}
            </Typography>
          </Box>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
            <Button variant="outlined" startIcon={showDeleted ? <Inventory2Outlined /> : <DeleteOutlineOutlined />} onClick={() => changeDeletedView(!showDeleted)}>
              {showDeleted ? "Back to inventory" : `Deleted records (${counts.deleted})`}
            </Button>
            {!showDeleted ? (
            <Button variant="contained" startIcon={<AddOutlined />} onClick={() => setActiveDialog({ type: "form", mode: "create" })} disabled={connection !== "ready"}>
                Add equipment
              </Button>
            ) : null}
          </Stack>
        </Stack>

        {!showDeleted ? <Box sx={{ display: "grid", gridTemplateColumns: { xs: "repeat(2, 1fr)", md: "repeat(3, 1fr)", lg: "repeat(6, 1fr)" }, gap: 1.5, mb: 2 }}>
          <Metric label="Inventory" value={counts.total} />
          <Metric label="Active" value={counts.active} tone="#24734f" />
          <Metric label="Due soon" value={counts.attention} tone="#b45309" />
          <Metric label="Overdue" value={counts.overdue} tone="#b42318" />
          <Metric label="Calibration failed" value={counts.failed} tone="#7f1d1d" />
          <Metric label="Out of service" value={counts.outOfService} tone="#59636e" />
        </Box> : null}

        <Paper variant="outlined">
          <Stack direction={{ xs: "column", md: "row" }} spacing={1.5} sx={{ p: 1.5 }}>
            <TextField
              size="small"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search asset, name, category or location"
              sx={{ flex: 1, minWidth: 240 }}
              slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchOutlined fontSize="small" /></InputAdornment> } }}
            />
            {!showDeleted ? <FormControl size="small" sx={{ minWidth: 180 }}>
              <InputLabel id="status-filter-label">Operational status</InputLabel>
              <Select labelId="status-filter-label" value={statusFilter} label="Operational status" onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}>
                <MenuItem value="ALL">All statuses</MenuItem>
                {Object.entries(statusMeta).filter(([value]) => value !== "DELETED").map(([value, meta]) => <MenuItem key={value} value={value}>{meta.label}</MenuItem>)}
              </Select>
            </FormControl> : null}
            <Tooltip title="Refresh inventory">
              <IconButton onClick={() => void load()} disabled={loading} sx={{ alignSelf: { xs: "flex-end", md: "center" }, border: "1px solid", borderColor: "divider", borderRadius: 1 }}>
                <RefreshOutlined fontSize="small" />
              </IconButton>
            </Tooltip>
          </Stack>
          <Divider />
          <TableContainer>
            <Table size="small" aria-label={showDeleted ? "Deleted equipment records" : "Equipment inventory"}>
              <TableHead>
                <TableRow>
                  <TableCell>Asset</TableCell>
                  <TableCell>Equipment</TableCell>
                  <TableCell sx={{ display: { xs: "none", sm: "table-cell" } }}>Location</TableCell>
                  <TableCell sx={{ display: { xs: "none", md: "table-cell" } }}>{showDeleted ? "Deleted at" : "Calibration due"}</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {loading ? (
                  <TableRow><TableCell colSpan={5} align="center" sx={{ py: 6 }}><CircularProgress size={28} /></TableCell></TableRow>
                ) : filteredEquipment.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} align="center" sx={{ py: 7 }}>
                      <Inventory2Outlined color="disabled" sx={{ fontSize: 40, mb: 1 }} />
                      <Typography sx={{ fontWeight: 650 }}>{showDeleted ? "No deleted records" : "No equipment matches this view"}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {showDeleted ? "Equipment removed from the inventory will appear here." : "Adjust the filters or add a new record."}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : filteredEquipment.map((item) => (
                  <TableRow
                    key={item.id}
                    hover
                    selected={item.id === selectedId}
                    tabIndex={0}
                    aria-label={`Open details for ${item.assetTag}`}
                    onClick={() => setSelectedId(item.id)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        setSelectedId(item.id);
                      }
                    }}
                    sx={{ cursor: "pointer", "&:focus-visible": { outline: "3px solid", outlineColor: "primary.light", outlineOffset: -3 } }}
                  >
                    <TableCell><Typography component="span" sx={{ fontFamily: "ui-monospace, Consolas, monospace", fontSize: "0.78rem", fontWeight: 750, color: "primary.main" }}>{item.assetTag}</Typography></TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 650 }}>{item.name}</Typography>
                      <Typography variant="caption" color="text.secondary">{item.category}</Typography>
                    </TableCell>
                    <TableCell sx={{ display: { xs: "none", sm: "table-cell" } }}>{item.location}</TableCell>
                    <TableCell sx={{ display: { xs: "none", md: "table-cell" } }}>{item.deletedAt ? `Deleted ${formatDateTime(item.deletedAt)}` : item.calibrationIntervalDays ? item.dueDate ? formatDate(item.dueDate) : "No successful calibration" : "Not required"}</TableCell>
                    <TableCell><StatusChip status={item.status} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <Box sx={{ px: 2, py: 1.25, bgcolor: "#f8f9fa", borderTop: "1px solid", borderColor: "divider" }}>
            <Typography variant="caption" color="text.secondary">{filteredEquipment.length} of {showDeleted ? counts.deleted : counts.total} records shown</Typography>
          </Box>
        </Paper>
      </Box>

      <Suspense fallback={null}>
      {selected ? (
        <EquipmentDetailsDrawer
          equipment={selected}
          onClose={() => setSelectedId(null)}
          onEdit={() => setActiveDialog({ type: "form", mode: "edit" })}
          onRecordEvent={() => setActiveDialog({ type: "event" })}
          onCorrect={(event) => setActiveDialog({ type: "correction", event })}
          onRestoreDeleted={() => setActiveDialog({ type: "restoreDeleted" })}
          onChangeState={() => setActiveDialog({ type: "state" })}
          onDelete={() => setActiveDialog({ type: "delete" })}
        />
      ) : null}
      {activeDialog?.type === "form" ? (
        <EquipmentFormDialog
          key={`${activeDialog.mode}-${selected?.id ?? "new"}`}
          mode={activeDialog.mode}
          equipment={activeDialog.mode === "edit" ? selected : undefined}
          open
          onClose={() => setActiveDialog(null)}
          onSaved={acceptSaved}
        />
      ) : null}
      {selected && activeDialog?.type === "event" ? <EventDialog key={selected.id} equipment={selected} open onClose={() => setActiveDialog(null)} onSaved={acceptSaved} /> : null}
      {selected && activeDialog?.type === "state" ? <StateDialog key={`${selected.id}-${selected.archivedAt ?? "active"}`} equipment={selected} open onClose={() => setActiveDialog(null)} onSaved={acceptSaved} /> : null}
      {selected && activeDialog?.type === "correction" ? <CorrectionDialog key={activeDialog.event.id} equipment={selected} event={activeDialog.event} open onClose={() => setActiveDialog(null)} onSaved={acceptSaved} /> : null}
      {selected && activeDialog?.type === "delete" ? (
        <DeleteEquipmentDialog
          key={selected.id}
          equipment={selected}
          open
          onClose={() => setActiveDialog(null)}
          onSaved={(deleted, text) => {
            setEquipment((current) => current.map((item) => (item.id === deleted.id ? deleted : item)));
            setSelectedId(null);
            setNotice({ severity: "success", text });
          }}
        />
      ) : null}
      {selected && activeDialog?.type === "restoreDeleted" ? (
        <RestoreDeletedDialog
          key={selected.id}
          equipment={selected}
          open
          onClose={() => setActiveDialog(null)}
          onSaved={(restored, text) => {
            setEquipment((current) => current.map((item) => (item.id === restored.id ? restored : item)));
            setSelectedId(null);
            setNotice({ severity: "success", text });
          }}
        />
      ) : null}

      </Suspense>

      <Snackbar open={Boolean(notice)} autoHideDuration={4500} onClose={() => setNotice(null)} anchorOrigin={{ vertical: "bottom", horizontal: "center" }}>
        {notice ? <Alert severity={notice.severity} variant="filled" onClose={() => setNotice(null)}>{notice.text}</Alert> : undefined}
      </Snackbar>
    </Box>
  );
}

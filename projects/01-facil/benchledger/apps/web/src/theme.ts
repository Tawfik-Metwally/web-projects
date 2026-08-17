import { createTheme } from "@mui/material/styles";

export const theme = createTheme({
  palette: {
    mode: "light",
    primary: { main: "#17324d", dark: "#0f2438", light: "#d8e4ee" },
    secondary: { main: "#b45309" },
    background: { default: "#eef1f4", paper: "#ffffff" },
    text: { primary: "#17212b", secondary: "#53606d" },
    divider: "#d5dbe1",
    success: { main: "#24734f" },
    warning: { main: "#a85d00" },
    error: { main: "#b42318" },
  },
  shape: { borderRadius: 5 },
  typography: {
    fontFamily: 'Inter, "Segoe UI", Arial, sans-serif',
    h1: { fontSize: "1.35rem", fontWeight: 700, letterSpacing: "-0.01em" },
    h2: { fontSize: "1.1rem", fontWeight: 700 },
    button: { fontWeight: 650, textTransform: "none" },
  },
  components: {
    MuiButton: { defaultProps: { disableElevation: true }, styleOverrides: { root: { borderRadius: 4 } } },
    MuiPaper: { styleOverrides: { root: { backgroundImage: "none" } } },
    MuiTableCell: {
      styleOverrides: {
        head: { backgroundColor: "#f5f7f9", color: "#465360", fontSize: "0.72rem", fontWeight: 750, textTransform: "uppercase", letterSpacing: "0.05em" },
        root: { borderColor: "#e0e5ea" },
      },
    },
  },
});

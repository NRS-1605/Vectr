const { runDetached } = require("./powershell");

const mouseApi = "Add-Type @'\nusing System; using System.Runtime.InteropServices; public static class VectrMouse { [DllImport(\"user32.dll\")] public static extern void mouse_event(int f,int x,int y,int d,IntPtr e); }\n'@;";
const keyboardApi = "Add-Type -AssemblyName System.Windows.Forms;";
const keyMap = { CTRL: "^", CONTROL: "^", ALT: "%", SHIFT: "+", ENTER: "{ENTER}", ESC: "{ESC}", ESCAPE: "{ESC}", TAB: "{TAB}", SPACE: " ", UP: "{UP}", DOWN: "{DOWN}", LEFT: "{LEFT}", RIGHT: "{RIGHT}", DELETE: "{DELETE}", BACKSPACE: "{BACKSPACE}", HOME: "{HOME}", END: "{END}" };

function psText(text) { return `[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('${Buffer.from(text, "utf8").toString("base64")}'))`; }
function escapeSendKeysText(text) {
  // SendKeys treats these as control syntax. Brace-wrapping makes each one a
  // literal character, so `type C++` and `type 50%` behave as users expect.
  return text.replace(/[+^%~(){}\[\]]/g, (character) => `{${character}}`);
}
function keypress(command) {
  const trimmed = command.trim();
  if (trimmed.startsWith("type ")) return runDetached(`${keyboardApi}[System.Windows.Forms.SendKeys]::SendWait(${psText(escapeSendKeysText(trimmed.slice(5)))})`);
  const parts = (trimmed.startsWith("key ") ? trimmed.slice(4) : trimmed).split(/[+\s]+/).filter(Boolean);
  const keys = parts.map((key) => keyMap[key.toUpperCase()] || (key.length === 1 ? key : `{${key.toUpperCase()}}`)).join("");
  return runDetached(`${keyboardApi}[System.Windows.Forms.SendKeys]::SendWait(${psText(keys)})`);
}
function move(dx, dy) { return runDetached(`${mouseApi}[VectrMouse]::mouse_event(1,${Math.round(dx)},${Math.round(dy)},0,[IntPtr]::Zero)`); }
function click(button) { return runDetached(`${mouseApi}[VectrMouse]::mouse_event(${button === "right" ? 8 : 2},0,0,0,[IntPtr]::Zero); [VectrMouse]::mouse_event(${button === "right" ? 16 : 4},0,0,0,[IntPtr]::Zero)`); }
function scroll(dy) { return runDetached(`${mouseApi}[VectrMouse]::mouse_event(2048,0,0,${Math.round(-dy * 10)},[IntPtr]::Zero)`); }

module.exports = { keypress, move, click, scroll };

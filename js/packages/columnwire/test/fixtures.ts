import { readFileSync } from "node:fs";
import { resolve } from "node:path";

export function fixtureBytes(name: string): Uint8Array<ArrayBuffer> {
  return Uint8Array.from(readFileSync(resolve(process.cwd(), "test/fixtures", name)));
}

export function fixtureText(name: string): string {
  return readFileSync(resolve(process.cwd(), "test/fixtures", name), "utf8");
}

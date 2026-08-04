const fs = require("fs");
const path = require("path");

const TEMPLATE = path.join(__dirname, "pet-home.template.html");
const OUT = path.join(__dirname, "hybrid", "html", "pet-home.html");
const GLB_DIR = path.join(__dirname, "static", "model", "cat");

let template = fs.readFileSync(TEMPLATE, "utf8");

if (template.includes("__PET_ANIMATIONS__")) {
  const assets = path.join(__dirname, "hybrid", "html", "assets", "pet");
  const names = ["idle", "happy", "eat", "sleep", "play"];
  const anims = {};
  for (const name of names) {
    const file = path.join(assets, name + ".json");
    if (!fs.existsSync(file)) throw new Error("missing animation: " + file);
    anims[name] = JSON.parse(fs.readFileSync(file, "utf8"));
  }
  template = template.replace("__PET_ANIMATIONS__", JSON.stringify(anims));
}

if (template.includes("__PET_GLB__")) {
  const files = fs.readdirSync(GLB_DIR).filter((f) => f.toLowerCase().endsWith(".glb"));
  if (files.length === 0) throw new Error("no glb found in " + GLB_DIR);
  const glb = fs.readFileSync(path.join(GLB_DIR, files[0]));
  template = template.replace("__PET_GLB__", glb.toString("base64"));
}

fs.writeFileSync(OUT, template, "utf8");
console.log("wrote pet-home.html", template.length, "bytes");
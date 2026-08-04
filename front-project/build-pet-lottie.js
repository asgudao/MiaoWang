const fs = require("fs");
const path = require("path");

const OUT_DIR = path.join(__dirname, "hybrid", "html", "assets", "pet");
const FR = 30;
const W = 512;
const H = 512;

const C = {
  body: [1, 0.549, 0.259, 1],       // #FF8C42
  deep: [0.929, 0.416, 0.133, 1],   // #ED6A22
  belly: [1, 0.769, 0.4, 1],        // #FFC466
  flame: [1, 0.42, 0.373, 1],       // #FF6B5F
  flameInner: [1, 0.82, 0.4, 1],    // #FFD166
  dark: [0.24, 0.173, 0.165, 1],    // #3D2C2A
  mouth: [0.541, 0.29, 0.165, 1],   // #8A4A2A
  cheek: [1, 0.69, 0.533, 1],       // #FFB088
  white: [1, 1, 1, 1],
  shadow: [0.16, 0.11, 0.09, 0.18],
  spark: [1, 0.827, 0.4, 1]         // #FFD366
};

function prop(v) {
  return { a: 0, k: v };
}

function kfArr(t, s, e) {
  return {
    t,
    s,
    e,
    o: { x: [0.42], y: [0] },
    i: { x: [0.58], y: [1] },
    to: [0, 0, 0],
    ti: [0, 0, 0]
  };
}

function kfNum(t, s, e) {
  return {
    t,
    s,
    e,
    o: { x: [0.42], y: [0] },
    i: { x: [0.58], y: [1] }
  };
}

function animArr(kfs) {
  return { a: 1, k: kfs };
}

function animNum(kfs) {
  return { a: 1, k: kfs };
}

function tr() {
  return {
    ty: "tr",
    p: prop([0, 0]),
    a: prop([0, 0]),
    s: prop([100, 100]),
    r: prop(0),
    o: prop(100),
    sk: prop(0),
    sa: prop(0)
  };
}

function ellipse(cx, cy, w, h, color, opacity) {
  return {
    ty: "el",
    nm: "shape",
    p: prop([cx, cy]),
    s: prop([w, h])
  };
}

function fill(color, opacity) {
  return { ty: "fl", c: { a: 0, k: color }, o: prop(opacity || 100), r: 1 };
}

function group(name, shapes) {
  return {
    ty: "gr",
    nm: name,
    it: shapes.concat([tr()]),
    bm: 0
  };
}

function shapeLayers(shapes) {
  return shapes.flatMap((s) => [ellipse(s[0], s[1], s[2], s[3], s[4], s[5]), fill(s[4], s[5])]);
}

function layer(ind, name, shapes, opts) {
  const o = opts || {};
  const cx = o.cx == null ? 256 : o.cx;
  const cy = o.cy == null ? 300 : o.cy;
  return {
    ddd: 0,
    ind,
    ty: 4,
    nm: name,
    sr: 1,
    ks: {
      o: o.o || prop(100),
      r: o.r || prop(0),
      p: o.p || prop([cx, cy, 0]),
      a: o.a || prop([0, 0, 0]),
      s: o.s || prop([100, 100, 100])
    },
    ao: 0,
    shapes,
    ip: 0,
    op: o.op || 120,
    st: 0,
    bm: 0
  };
}

function makeJson(name, frames, patch) {
  const layers = [
    layer(1, "shadow", [group("shadow", shapeLayers([[0, 0, 224, 38, C.shadow, 100]]))], { cy: 426, op: frames }),
    layer(2, "feet", [group("feet", shapeLayers([[-44, 0, 58, 34, C.deep, 100], [44, 0, 58, 34, C.deep, 100]]))], { cy: 408, op: frames }),
    layer(3, "body", [group("body", shapeLayers([[0, 0, 190, 216, C.body, 100]]))], { op: frames }),
    layer(4, "belly", [group("belly", shapeLayers([[0, 28, 118, 130, C.belly, 100]]))], { cy: 332, op: frames }),
    layer(5, "arms", [group("arms", shapeLayers([[-98, 10, 42, 118, C.deep, 100], [98, 10, 42, 118, C.deep, 100]]))], { op: frames }),
    layer(6, "flame-outer", [group("flame-outer", shapeLayers([[0, 0, 118, 168, C.flame, 100]]))], { cy: 142, op: frames }),
    layer(7, "flame-inner", [group("flame-inner", shapeLayers([[0, 0, 74, 112, C.flameInner, 100]]))], { cy: 154, op: frames }),
    layer(8, "eyes", [group("eyes", shapeLayers([[-28, 0, 20, 28, C.dark, 100], [28, 0, 20, 28, C.dark, 100]]))], { cy: 278, op: frames }),
    layer(9, "eye-highlights", [group("eye-highlights", shapeLayers([[-30, -4, 8, 8, C.white, 100], [26, -4, 8, 8, C.white, 100]]))], { cy: 274, op: frames }),
    layer(10, "mouth", [group("mouth", shapeLayers([[0, 0, 32, 16, C.mouth, 100]]))], { cy: 314, op: frames }),
    layer(11, "cheeks", [group("cheeks", shapeLayers([[-52, 4, 38, 24, C.cheek, 100], [52, 4, 38, 24, C.cheek, 100]]))], { op: frames })
  ];

  if (patch) patch(layers);

  return {
    v: "5.7.4",
    fr: FR,
    ip: 0,
    op: frames,
    w: W,
    h: H,
    nm: name,
    ddd: 0,
    assets: [],
    layers
  };
}

function addSparkles(layers, frames, points, stagger) {
  const startInd = layers.length + 1;
  points.forEach((pt, idx) => {
    const ind = startInd + idx;
    const kfs = [];
    const cycle = Math.floor(frames / 2);
    const off = idx * (stagger || 8);
    for (let t = off; t <= frames; t += cycle) {
      kfs.push(kfNum(t, 0, 0), kfNum(t + 6, 0, 85), kfNum(t + 18, 85, 0));
    }
    layers.push(
      layer(ind, "spark-" + idx, [group("spark", shapeLayers([[0, 0, 16, 16, C.spark, 100]]))], {
        cx: pt[0],
        cy: pt[1],
        o: animNum(kfs),
        op: frames
      })
    );
  });
}

function idle() {
  const frames = 120;
  const anim = makeJson("idle", frames, (layers) => {
    layers[2].ks.s = animArr([
      kfArr(0, [100, 100, 100], [100, 104, 100]),
      kfArr(30, [100, 104, 100], [100, 100, 100]),
      kfArr(60, [100, 100, 100], [100, 104, 100]),
      kfArr(90, [100, 104, 100], [100, 100, 100]),
      kfArr(120, [100, 100, 100], [100, 100, 100])
    ]);
    layers[2].ks.p = animArr([
      kfArr(0, [256, 300, 0], [256, 295, 0]),
      kfArr(30, [256, 295, 0], [256, 300, 0]),
      kfArr(60, [256, 300, 0], [256, 295, 0]),
      kfArr(90, [256, 295, 0], [256, 300, 0]),
      kfArr(120, [256, 300, 0], [256, 300, 0])
    ]);
    layers[5].ks.s = animArr([
      kfArr(0, [100, 100, 100], [100, 112, 100]),
      kfArr(20, [100, 112, 100], [100, 100, 100]),
      kfArr(40, [100, 100, 100], [100, 112, 100]),
      kfArr(60, [100, 112, 100], [100, 100, 100]),
      kfArr(80, [100, 100, 100], [100, 112, 100]),
      kfArr(100, [100, 112, 100], [100, 100, 100]),
      kfArr(120, [100, 100, 100], [100, 100, 100])
    ]);
    layers[6].ks.s = animArr([
      kfArr(0, [100, 100, 100], [100, 118, 100]),
      kfArr(26, [100, 118, 100], [100, 100, 100]),
      kfArr(52, [100, 100, 100], [100, 118, 100]),
      kfArr(78, [100, 118, 100], [100, 100, 100]),
      kfArr(104, [100, 100, 100], [100, 118, 100]),
      kfArr(120, [100, 118, 100], [100, 100, 100])
    ]);
    layers[0].ks.s = animArr([
      kfArr(0, [100, 100, 100], [94, 100, 100]),
      kfArr(30, [94, 100, 100], [100, 100, 100]),
      kfArr(60, [100, 100, 100], [94, 100, 100]),
      kfArr(90, [94, 100, 100], [100, 100, 100]),
      kfArr(120, [100, 100, 100], [100, 100, 100])
    ]);
    layers[7].ks.s = animArr([
      kfArr(0, [100, 100, 100], [100, 100, 100]),
      kfArr(54, [100, 100, 100], [100, 8, 100]),
      kfArr(58, [100, 8, 100], [100, 100, 100]),
      kfArr(112, [100, 100, 100], [100, 8, 100]),
      kfArr(116, [100, 8, 100], [100, 100, 100]),
      kfArr(120, [100, 100, 100], [100, 100, 100])
    ]);
    addSparkles(layers, frames, [[150, 178], [364, 218]], 18);
  });
  write(anim, "idle.json");
}

function happy() {
  const frames = 96;
  const anim = makeJson("happy", frames, (layers) => {
    layers[2].ks.p = animArr([
      kfArr(0, [256, 300, 0], [256, 250, 0]),
      kfArr(28, [256, 250, 0], [256, 300, 0]),
      kfArr(52, [256, 300, 0], [256, 272, 0]),
      kfArr(72, [256, 272, 0], [256, 300, 0]),
      kfArr(96, [256, 300, 0], [256, 300, 0])
    ]);
    layers[2].ks.s = animArr([
      kfArr(0, [100, 100, 100], [112, 112, 100]),
      kfArr(24, [112, 112, 100], [96, 96, 100]),
      kfArr(48, [96, 96, 100], [100, 100, 100]),
      kfArr(72, [100, 100, 100], [106, 106, 100]),
      kfArr(96, [106, 106, 100], [100, 100, 100])
    ]);
    layers[4].ks.r = animArr([
      kfNum(0, -12, 22),
      kfNum(28, 22, -12),
      kfNum(56, -12, 12),
      kfNum(80, 12, -12),
      kfNum(96, -12, -12)
    ]);
    layers[0].ks.s = animArr([
      kfArr(0, [100, 100, 100], [86, 100, 100]),
      kfArr(28, [86, 100, 100], [100, 100, 100]),
      kfArr(52, [100, 100, 100], [92, 100, 100]),
      kfArr(72, [92, 100, 100], [100, 100, 100]),
      kfArr(96, [100, 100, 100], [100, 100, 100])
    ]);
    layers[5].ks.s = animArr([
      kfArr(0, [100, 100, 100], [100, 112, 100]),
      kfArr(20, [100, 112, 100], [100, 100, 100]),
      kfArr(40, [100, 100, 100], [100, 112, 100]),
      kfArr(60, [100, 112, 100], [100, 100, 100]),
      kfArr(80, [100, 100, 100], [100, 112, 100]),
      kfArr(96, [100, 112, 100], [100, 100, 100])
    ]);
    layers[9].ks.s = animArr([
      kfArr(0, [100, 100, 100], [150, 100, 100]),
      kfArr(28, [150, 100, 100], [100, 100, 100]),
      kfArr(96, [100, 100, 100], [100, 100, 100])
    ]);
    addSparkles(layers, frames, [[140, 210], [372, 190], [256, 120]], 10);
  });
  write(anim, "happy.json");
}

function eat() {
  const frames = 150;
  const anim = makeJson("eat", frames, (layers) => {
    const chew = [];
    for (let t = 0; t < frames; t += 30) {
      chew.push(
        kfArr(t, [100, 100, 100], [100, 100, 100]),
        kfArr(t + 5, [100, 100, 100], [100, 145, 100]),
        kfArr(t + 12, [100, 145, 100], [100, 100, 100])
      );
    }
    chew.push(kfArr(frames, [100, 100, 100], [100, 100, 100]));
    layers[9].ks.s = animArr(chew);

    layers[2].ks.s = animArr([
      kfArr(0, [100, 100, 100], [100, 102, 100]),
      kfArr(75, [100, 102, 100], [100, 100, 100]),
      kfArr(150, [100, 100, 100], [100, 100, 100])
    ]);
    layers[5].ks.s = animArr([
      kfArr(0, [100, 100, 100], [100, 110, 100]),
      kfArr(25, [100, 110, 100], [100, 100, 100]),
      kfArr(50, [100, 100, 100], [100, 110, 100]),
      kfArr(75, [100, 110, 100], [100, 100, 100]),
      kfArr(100, [100, 100, 100], [100, 110, 100]),
      kfArr(125, [100, 110, 100], [100, 100, 100]),
      kfArr(150, [100, 100, 100], [100, 100, 100])
    ]);

    const foodOp = [];
    for (let t = 6; t < frames; t += 30) {
      foodOp.push(kfNum(t, 0, 0), kfNum(t + 8, 0, 95), kfNum(t + 22, 95, 0));
    }
    foodOp.push(kfNum(frames, 0, 0));
    layers.push(
      layer(12, "food", [group("food", shapeLayers([[0, 0, 48, 48, C.flame, 100], [10, -8, 18, 18, C.flameInner, 100]]))], {
        cx: 308,
        cy: 252,
        o: animNum(foodOp),
        op: frames
      })
    );
  });
  write(anim, "eat.json");
}

function sleep() {
  const frames = 180;
  const anim = makeJson("sleep", frames, (layers) => {
    layers[2].ks.s = animArr([
      kfArr(0, [100, 100, 100], [100, 105, 100]),
      kfArr(60, [100, 105, 100], [100, 100, 100]),
      kfArr(120, [100, 100, 100], [100, 105, 100]),
      kfArr(180, [100, 105, 100], [100, 100, 100])
    ]);
    layers[7].ks.s = animArr([
      kfArr(0, [100, 12, 100], [100, 12, 100]),
      kfArr(180, [100, 12, 100], [100, 12, 100])
    ]);
    layers[5].ks.s = animArr([
      kfArr(0, [100, 100, 100], [100, 104, 100]),
      kfArr(90, [100, 104, 100], [100, 100, 100]),
      kfArr(180, [100, 100, 100], [100, 100, 100])
    ]);
    layers[5].ks.o = animNum([kfNum(0, 100, 100), kfNum(90, 100, 70), kfNum(180, 70, 70)]);
    layers[6].ks.o = animNum([kfNum(0, 100, 100), kfNum(90, 100, 76), kfNum(180, 76, 76)]);

    const zzz = [
      { cx: 330, cy: 208, endY: 128, start: 0, size: 18 },
      { cx: 352, cy: 168, endY: 92, start: 60, size: 24 },
      { cx: 376, cy: 128, endY: 56, start: 120, size: 14 }
    ];
    zzz.forEach((z, idx) => {
      const ind = 12 + idx;
      const op = [];
      const pos = [];
      for (let t = z.start; t <= frames; t += 60) {
        const p0 = [z.cx, z.cy, 0];
        const p1 = [z.cx + 6, z.cy - 10, 0];
        const p2 = [z.cx + 12, z.endY, 0];
        pos.push(kfArr(t, p0, p1), kfArr(t + 14, p1, p2), kfArr(t + 44, p2, p2));
        op.push(kfNum(t, 0, 0), kfNum(t + 10, 0, 85), kfNum(t + 32, 85, 0));
      }
      layers.push(
        layer(ind, "zzz-" + idx, [group("zzz", shapeLayers([[0, 0, z.size, z.size, C.belly, 100]]))], {
          p: animArr(pos),
          o: animNum(op),
          op: frames
        })
      );
    });
  });
  write(anim, "sleep.json");
}

function play() {
  const frames = 120;
  const anim = makeJson("play", frames, (layers) => {
    layers[2].ks.r = animArr([
      kfNum(0, 0, -14),
      kfNum(20, -14, 14),
      kfNum(40, 14, 0),
      kfNum(60, 0, -10),
      kfNum(80, -10, 10),
      kfNum(100, 10, 0),
      kfNum(120, 0, 0)
    ]);
    layers[2].ks.p = animArr([
      kfArr(0, [256, 300, 0], [256, 272, 0]),
      kfArr(30, [256, 272, 0], [256, 300, 0]),
      kfArr(60, [256, 300, 0], [256, 284, 0]),
      kfArr(90, [256, 284, 0], [256, 300, 0]),
      kfArr(120, [256, 300, 0], [256, 300, 0])
    ]);
    layers[2].ks.s = animArr([
      kfArr(0, [100, 100, 100], [106, 100, 100]),
      kfArr(20, [106, 100, 100], [98, 100, 100]),
      kfArr(40, [98, 100, 100], [100, 100, 100]),
      kfArr(60, [100, 100, 100], [104, 100, 100]),
      kfArr(80, [104, 100, 100], [98, 100, 100]),
      kfArr(100, [98, 100, 100], [100, 100, 100]),
      kfArr(120, [100, 100, 100], [100, 100, 100])
    ]);
    layers[4].ks.r = animArr([
      kfNum(0, -10, 18),
      kfNum(30, 18, -10),
      kfNum(60, -10, 14),
      kfNum(90, 14, -10),
      kfNum(120, -10, -10)
    ]);
    layers[5].ks.s = animArr([
      kfArr(0, [100, 100, 100], [100, 114, 100]),
      kfArr(24, [100, 114, 100], [100, 100, 100]),
      kfArr(48, [100, 100, 100], [100, 114, 100]),
      kfArr(72, [100, 114, 100], [100, 100, 100]),
      kfArr(96, [100, 100, 100], [100, 114, 100]),
      kfArr(120, [100, 114, 100], [100, 100, 100])
    ]);
    addSparkles(layers, frames, [[132, 224], [380, 236], [256, 104], [196, 160]], 7);
  });
  write(anim, "play.json");
}

function write(anim, filename) {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.writeFileSync(path.join(OUT_DIR, filename), JSON.stringify(anim, null, 2), "utf8");
  console.log("wrote", filename);
}

idle();
happy();
eat();
sleep();
play();

const fs = require('fs');

const glbPath = 'E:/MapleLeaf/project/MiaoWang/front-project/hybrid/html/chonky_cat_trio.glb';
const outPath = 'E:/MapleLeaf/project/MiaoWang/front-project/hybrid/html/cat-3d.html';

const glb = fs.readFileSync(glbPath);
const b64 = glb.toString('base64');

const html = `<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
<style>
* { margin: 0; padding: 0; }
body { overflow: hidden; background: #FFF8F0; font-family: sans-serif; }
canvas { display: block; }
#loading { position: fixed; top: 50%; left: 50%; transform: translate(-50%,-50%); color: #FF8C42; font-size: 18px; z-index: 100; pointer-events: none; }
#hint { position: fixed; bottom: 80px; left: 50%; transform: translateX(-50%); color: #AAA; font-size: 12px; z-index: 50; pointer-events: none; }
</style>
</head>
<body>
<div id="loading">Loading Cat...</div>
<div id="hint">Tap to bounce</div>
<script type="importmap">
{ "imports": { "three": "https://unpkg.com/three@0.160.0/build/three.module.js", "three/addons/": "https://unpkg.com/three@0.160.0/examples/jsm/" } }
</script>
<script type="module">
import * as THREE from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";

const GLB_BASE64 = "${b64}";
const loading = document.getElementById("loading");
const hint = document.getElementById("hint");
const w = window.innerWidth, h = window.innerHeight;

const scene = new THREE.Scene();
scene.background = new THREE.Color("#FFF8F0");

const camera = new THREE.PerspectiveCamera(40, w/h, 0.1, 50);
camera.position.set(0, 1.5, 6);
camera.lookAt(0, 0.3, 0);

const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
renderer.setSize(w, h);
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.3;
renderer.outputColorSpace = THREE.SRGBColorSpace;
document.body.appendChild(renderer.domElement);

// Ambient light only - no shadows, no directional light shadows
scene.add(new THREE.AmbientLight("#FFFFFF", 3));
scene.add(new THREE.DirectionalLight("#FFFFFF", 2).set(2, 4, 3));
scene.add(new THREE.DirectionalLight("#FFE0C0", 1).set(-2, 1, -1));

// Controls: rotate only, no zoom/pan
const controls = new OrbitControls(camera, renderer.domElement);
controls.target.set(0, 0.3, 0);
controls.enableDamping = true; controls.dampingFactor = 0.08;
controls.enableZoom = false;
controls.enablePan = false;
controls.enableRotate = true;
controls.minPolarAngle = 0.3; controls.maxPolarAngle = Math.PI * 0.6;
controls.autoRotate = true; controls.autoRotateSpeed = 0.4;
controls.update();

let mixer = null, catGroup = null, baseScale = 1;
const clock = new THREE.Clock();

const binaryStr = atob(GLB_BASE64);
const bytes = new Uint8Array(binaryStr.length);
for (let i = 0; i < binaryStr.length; i++) bytes[i] = binaryStr.charCodeAt(i);

new GLTFLoader().parse(bytes.buffer, '', (gltf) => {
  loading.style.display = 'none';
  catGroup = gltf.scene;
  gltf.scene.traverse((node) => {
    if (node.isMesh) {
      node.castShadow = false;
      node.receiveShadow = false;
    }
  });
  const box = new THREE.Box3().setFromObject(catGroup);
  const size = box.getSize(new THREE.Vector3());
  baseScale = 2.8 / Math.max(size.x, size.y, size.z);
  catGroup.scale.setScalar(baseScale);
  const center = box.getCenter(new THREE.Vector3());
  catGroup.position.set(-center.x * baseScale, -center.y * baseScale + 0.15, -center.z * baseScale);
  scene.add(catGroup);
  if (gltf.animations.length > 0) {
    mixer = new THREE.AnimationMixer(catGroup);
    gltf.animations.forEach((clip) => { mixer.clipAction(clip).play(); });
  }
}, (p) => {
  loading.textContent = 'Loading... ' + Math.round(p.loaded/1024) + 'KB';
}, (err) => {
  loading.textContent = 'Failed: ' + (err ? (err.message || String(err)) : 'unknown');
});

// Click bounce
const raycaster = new THREE.Raycaster();
const mouse = new THREE.Vector2();
let bounce = 0;

renderer.domElement.addEventListener('click', (e) => {
  mouse.x = (e.clientX/w)*2-1; mouse.y = -(e.clientY/h)*2+1;
  raycaster.setFromCamera(mouse, camera);
  if (catGroup && raycaster.intersectObject(catGroup, true).length > 0) { bounce = 1; hint.style.opacity = '0'; }
});

function animate() {
  requestAnimationFrame(animate);
  const dt = Math.min(clock.getDelta(), 0.1);
  controls.update();
  if (mixer) mixer.update(dt);
  if (bounce > 0 && catGroup) {
    bounce -= dt * 2.5;
    const t = Math.max(0, bounce);
    catGroup.scale.setScalar(baseScale * (1 + Math.sin(t * Math.PI) * 0.12));
    if (bounce <= 0) { catGroup.scale.setScalar(baseScale); hint.style.opacity = '1'; }
  }
  renderer.render(scene, camera);
}
window.addEventListener('resize', () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
});
animate();
</script>
</body>
</html>`;

fs.writeFileSync(outPath, html, 'utf8');
console.log('Done! Size: ' + (fs.statSync(outPath).size / 1024 / 1024).toFixed(1) + 'MB');
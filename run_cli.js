const { spawnSync } = require('child_process');
const cli = 'E:\\Builder\\HBuilderX\\cli.exe';
const cwd = 'E:\\MapleLeaf\\project\\MiaoWang\\front-project';

// Run launch directly
const result = spawnSync(cli, ['launch', 'app-android', '--project', cwd, '--deviceId', '10AD630NX20011H'], {
    cwd: cwd,
    stdio: 'inherit'
});
process.exit(result.status);

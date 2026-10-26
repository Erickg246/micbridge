const { readdir, lstat, chmod } = require('node:fs/promises');
const { join } = require('node:path');
module.exports = async function afterPack(context) {
  async function fix(path) {
    const stat = await lstat(path);
    if (stat.isSymbolicLink()) return;
    await chmod(path, stat.isDirectory() || (stat.mode & 0o111) ? 0o755 : 0o644);
    if (stat.isDirectory()) for (const name of await readdir(path)) await fix(join(path, name));
  }
  await fix(context.appOutDir);
};

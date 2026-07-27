const fs = require('fs');
const utf8 = {encoding:'utf8'};
function clean(s) {
  return s.replace(/<nav-bar><\/nav-bar>/g, '').replace(/<view class=\"kw-body\">/g, '').replace(/<view class=\"pl-body\">/g, '').replace(/<view class=\"page-body\">/g, '');
}

['pages/pets/list.uvue','pages/knowledge/list.uvue','pages/mine/index.uvue'].forEach(f=>{
  const p = 'E:/MapleLeaf/project/MiaoWang/front-project/' + f;
  fs.writeFileSync(p, clean(fs.readFileSync(p,'utf8')), utf8);
  console.log(f + ' done');
});
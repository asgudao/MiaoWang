package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.biz.user.UserContext;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.dao.entity.PetDO;
import com.MapleLeaf.MiaoWang.dao.mapper.PetMapper;
import com.MapleLeaf.MiaoWang.dto.req.PetCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.PetUpdateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PetRespDTO;
import com.MapleLeaf.MiaoWang.service.PetService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PetServiceImpl extends ServiceImpl<PetMapper,PetDO> implements PetService {

    @Override
    public List<PetRespDTO> getMyPets() {
        // 当前用户身份从上下文获取，按稳定键 uid 查询（username 可被修改，不能作为归属键）
        String userId = UserContext.getUserId();
        if (!StrUtil.isNotBlank(userId)) {
            throw new ClientException("用户未登录或登录已过期");
        }
        LambdaQueryWrapper<PetDO> queryWrapper = Wrappers.lambdaQuery(PetDO.class)
                .eq(PetDO::getUid, Long.parseLong(userId))
                .eq(PetDO::getDelFlag, 0);
        List<PetDO> petDOList = baseMapper.selectList(queryWrapper);
        // 空列表直接返回空数组，前端渲染"还没有宠物"的空状态，不做客户端错误处理
        if (petDOList == null || petDOList.isEmpty()) {
            return List.of();
        }
        return petDOList.stream()
                .map(petDO -> BeanUtil.toBean(petDO, PetRespDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    public PetRespDTO createPetByUsername(PetCreateReqDTO requestParam) {
        // 用户身份一律从上下文获取（由网关/过滤器鉴权后注入），不信任前端传入的身份
        String userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        if (!StrUtil.isAllNotBlank(userId, username)) {
            throw new ClientException("用户未登录或登录已过期");
        }
        PetDO petDO = BeanUtil.toBean(requestParam, PetDO.class);
        petDO.setPid(null);
        // 数据库关联用 uid，username 冗余存储便于排查展示
        petDO.setUid(Long.parseLong(userId));
        petDO.setUsername(username);
        baseMapper.insert(petDO);
        // MP 会把自增主键回填到 petDO.pid，直接返回新宠物对象，前端无需二次查询
        return BeanUtil.toBean(petDO, PetRespDTO.class);
    }

    @Override
    public Void updatePetByPid(Long pid, PetUpdateReqDTO requestParam) {
        // 当前用户身份从上下文获取
        String userId = UserContext.getUserId();
        if (!StrUtil.isNotBlank(userId)) {
            throw new ClientException("用户未登录或登录已过期");
        }
        LambdaQueryWrapper<PetDO> queryWrapper = Wrappers.lambdaQuery(PetDO.class)
                .eq(PetDO::getPid, pid)
                .eq(PetDO::getUid, Long.parseLong(userId))
                .eq(PetDO::getDelFlag, 0);
        PetDO petDO = baseMapper.selectOne(queryWrapper);
        if (petDO == null) {
            throw new ClientException("宠物不存在或已被删除");
        }

        // 只复制 DTO 中存在的字段（pName/ownerCallName/age），pid/uid/username 保持不变
        BeanUtil.copyProperties(requestParam, petDO);
        baseMapper.updateById(petDO);
        return null;
    }

    @Override
    public Void deletePetByPid(Long pid) {
        // 当前用户身份从上下文获取
        String userId = UserContext.getUserId();
        if (!StrUtil.isNotBlank(userId)) {
            throw new ClientException("用户未登录或登录已过期");
        }
        // 存在性 + 归属 + 未删除 一次查询完成
        LambdaQueryWrapper<PetDO> queryWrapper = Wrappers.lambdaQuery(PetDO.class)
                .eq(PetDO::getPid, pid)
                .eq(PetDO::getUid, Long.parseLong(userId))
                .eq(PetDO::getDelFlag, 0);
        PetDO petDO = baseMapper.selectOne(queryWrapper);
        if (petDO == null) {
            throw new ClientException("宠物不存在或无权删除");
        }
        // 逻辑删除：置删除标记并记录删除时间，保留数据（delFlag：未删除0/已删除1）
        petDO.setDelFlag(1);
        petDO.setDeletionTime(System.currentTimeMillis());
        baseMapper.updateById(petDO);
        return null;
    }
}

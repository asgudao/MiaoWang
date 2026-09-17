package com.MapleLeaf.MiaoWang.bootstrap;

import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.dao.entity.UserDO;
import com.MapleLeaf.MiaoWang.dao.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时把存量用户名预热进布隆过滤器
 * <p>
 * 为什么必须有它：注册判断"用户名是否已存在"只看布隆过滤器、**不查库**——这正是它的价值，
 * 用一次 Redis 位运算换掉一次 DB 查询。但过滤器里是空的，存量用户名不在里面，
 * 检查就会整片漏过去 → 能注册出重名用户 → 之后按用户名登录 `selectOne` 查出多行直接抛 TooManyResults，
 * **这个用户名谁都登不上**。
 * <p>
 * 过滤器本身是 Redis 里的持久化结构，正常重启不会丢；预热是为了兜住三种情况：
 * Redis 被清库、换了 Redis 实例、指纹参数重建（tryInit 参数变了要重建）。
 * <p>
 * 关于删除的用户：这里**不过滤 del_flag**，即注销过的用户名也保持"已占用"。
 * 因为按用户名查用户的那条 SQL 没有 del_flag 条件，一旦允许重名，老账号和新账号会一起被查出来。
 * <p>
 * 读法用**游标（keyset）分页**而不是 LIMIT offset：offset 深翻页要跳过前面所有行，越大越慢；
 * 按自增主键 uid 递进则可以一直走主键索引。分批是为了不把整表一次性读进 JVM。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisterBloomFilterPreload implements ApplicationRunner {

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final UserMapper userMapper;

    /**
     * 每批读取的用户名条数
     */
    @Value("${user.register.bloom-preload-batch-size:1000}")
    private Integer bloomPreloadBatchSize;

    @Override
    public void run(ApplicationArguments args) {
        long start = System.currentTimeMillis();
        long lastUid = 0L;
        long total = 0L;
        while (true) {
            List<UserDO> batch = userMapper.selectList(Wrappers.lambdaQuery(UserDO.class)
                    .select(UserDO::getUid, UserDO::getUsername)
                    .gt(UserDO::getUid, lastUid)
                    .orderByAsc(UserDO::getUid)
                    .last("limit " + bloomPreloadBatchSize));
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (UserDO userDO : batch) {
                if (StrUtil.isNotBlank(userDO.getUsername())) {
                    // 幂等：已在过滤器里的用户名再 add 一次，只是重算一遍 hash 位，不会重复占位
                    userRegisterCachePenetrationBloomFilter.add(userDO.getUsername());
                }
                lastUid = userDO.getUid();
            }
            total += batch.size();
            // 取不满一批说明已经到表尾；uid 严格递增，所以循环一定会结束
            if (batch.size() < bloomPreloadBatchSize) {
                break;
            }
        }
        log.info("用户名布隆过滤器预热完成：加载 {} 个用户名，耗时 {} ms", total, System.currentTimeMillis() - start);
    }
}
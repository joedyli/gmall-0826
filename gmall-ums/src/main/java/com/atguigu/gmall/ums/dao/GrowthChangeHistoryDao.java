package com.atguigu.gmall.ums.dao;

import com.atguigu.gmall.ums.entity.GrowthChangeHistoryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 成长值变化历史记录
 * 
 * @author lixianfeng
 * @email lxf@atguigu.com
 * @date 2020-03-04 11:20:08
 */
@Mapper
public interface GrowthChangeHistoryDao extends BaseMapper<GrowthChangeHistoryEntity> {
	
}

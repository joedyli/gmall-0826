package com.atguigu.gmall.pms.service;

import com.atguigu.gmall.pms.vo.GroupVO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.pms.entity.AttrGroupEntity;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.QueryCondition;

import java.util.List;


/**
 * 属性分组
 *
 * @author lixianfeng
 * @email lxf@atguigu.com
 * @date 2020-02-18 14:09:27
 */
public interface AttrGroupService extends IService<AttrGroupEntity> {

    PageVo queryPage(QueryCondition params);

    PageVo queryGroupsByCid(QueryCondition queryCondition, Long cid);

    GroupVO queryGroupVOById(Long id);

    List<GroupVO> queryGroupWithAttrByCid(Long cid);
}


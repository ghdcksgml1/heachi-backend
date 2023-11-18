package com.heachi.mysql.define.housework.member.repository;

import com.heachi.mysql.define.housework.info.HouseworkInfo;
import com.heachi.mysql.define.housework.member.HouseworkMember;

import java.util.List;

public interface HouseworkMemberRepositoryCustom {
    // hosueworkInfo의 담당자 리스트를 뽑은 후 groupMemberIdList와 동일하지 않다면 houseworkMember 전부 삭제 후 false 리턴
    public boolean deleteHouseworkMemberIfGroupMemberIdIn(HouseworkInfo info, List<Long> groupMemberIdList);
}

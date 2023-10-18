package com.heachi.housework.api.service.housework.info;

import com.heachi.admin.common.exception.ExceptionMessage;
import com.heachi.admin.common.exception.group.member.GroupMemberException;
import com.heachi.admin.common.exception.housework.HouseworkException;
import com.heachi.external.clients.auth.response.UserInfoResponse;
import com.heachi.housework.api.service.housework.info.request.HouseworkInfoCreateServiceRequest;
import com.heachi.mysql.define.group.info.repository.GroupInfoRepository;
import com.heachi.mysql.define.group.member.GroupMember;
import com.heachi.mysql.define.group.member.repository.GroupMemberRepository;
import com.heachi.mysql.define.housework.category.HouseworkCategory;
import com.heachi.mysql.define.housework.category.repository.HouseworkCategoryRepository;
import com.heachi.mysql.define.housework.info.HouseworkInfo;
import com.heachi.mysql.define.housework.info.repository.HouseworkInfoRepository;
import com.heachi.mysql.define.housework.member.HouseworkMember;
import com.heachi.mysql.define.housework.member.repository.HouseworkMemberRepository;
import com.heachi.mysql.define.housework.save.repository.HouseworkSaveRepository;
import com.heachi.mysql.define.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HouseworkInfoService {
    private final UserRepository userRepository;
    private final GroupInfoRepository groupInfoRepository;
    private final HouseworkSaveRepository houseworkSaveRepository;
    private final HouseworkInfoRepository houseworkInfoRepository;
    private final HouseworkMemberRepository houseworkMemberRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final HouseworkCategoryRepository houseworkCategoryRepository;

    @Transactional(readOnly = false)
    public void createHouseworkInfo(HouseworkInfoCreateServiceRequest request) {
        try {
            // HOUSEWORK_CATEGORY 조회
            HouseworkCategory category = houseworkCategoryRepository.findById(request.getHouseworkCategoryId()).orElseThrow(() -> {
                log.warn(">>>> HouseworkCategory Not Found : {}", ExceptionMessage.HOUSEWORK_CATEGORY_NOT_FOUND);
                throw new HouseworkException(ExceptionMessage.HOUSEWORK_CATEGORY_NOT_FOUND);
            });

            // HOUSEWORK_INFO 생성
            HouseworkInfo houseworkInfo = HouseworkInfo.builder()
                    .houseworkCategory(category)
                    .title(request.getTitle())
                    .detail(request.getDetail())
                    .type(request.getType())
                    .dayDate(request.getDayDate())
                    .weekDate(request.getWeekDate())
                    .monthDate(request.getMonthDate())
                    .endTime(request.getEndTime())
                    .build();

            // HOUSEWORK_INFO 저장
            HouseworkInfo savedHousework = houseworkInfoRepository.save(houseworkInfo);
            log.info(">>>> HouseworkInfo Create: {}", savedHousework);


            // 담당자 지정 - HOUSEWORK_MEMBER 생성
            List<GroupMember> groupMemberList = groupMemberRepository.findGroupMemberListByGroupMemberIdList(request.getGroupMemberIdList());

            // 한 건이라도 조회 실패시 예외 발생
            if (groupMemberList.size() != request.getGroupMemberIdList().size()) {
                log.warn(">>>> GourpMember Not Found : {}", ExceptionMessage.GROUP_MEMBER_NOT_FOUND);
                throw new GroupMemberException(ExceptionMessage.GROUP_MEMBER_NOT_FOUND);
            }

            for (GroupMember gm : groupMemberList) {
                HouseworkMember hm = HouseworkMember.builder()
                        .groupMember(gm)
                        .houseworkInfo(houseworkInfo)
                        .build();
                // HOUSEWORK_MEMBER 저장
                houseworkMemberRepository.save(hm);
            }

        } catch (RuntimeException e) {
            log.warn(">>>> Housework Add Fail : {}", e.getMessage());
            throw e;
        }
    }
}
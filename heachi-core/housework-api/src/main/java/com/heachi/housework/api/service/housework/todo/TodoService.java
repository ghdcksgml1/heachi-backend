package com.heachi.housework.api.service.housework.todo;

import com.heachi.admin.common.utils.CachingStrategy;
import com.heachi.admin.common.utils.DayOfWeekUtils;
import com.heachi.housework.api.service.housework.todo.request.TodoSelectRequest;
import com.heachi.housework.api.service.housework.todo.response.TodoResponse;
import com.heachi.mysql.define.group.member.repository.GroupMemberRepository;
import com.heachi.mysql.define.housework.info.repository.HouseworkInfoRepository;
import com.heachi.mysql.define.housework.todo.HouseworkTodo;
import com.heachi.mysql.define.housework.todo.repository.HouseworkTodoRepository;
import com.heachi.mysql.define.user.User;
import com.heachi.redis.define.housework.todo.Todo;
import com.heachi.redis.define.housework.todo.TodoList;
import com.heachi.redis.define.housework.todo.TodoUser;
import com.heachi.redis.define.housework.todo.repository.TodoListRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoService {

    private final TodoListRepository todoListRepository;

    private final GroupMemberRepository groupMemberRepository;
    private final HouseworkTodoRepository houseworkTodoRepository;
    private final HouseworkInfoRepository houseworkInfoRepository;

    public TodoList cachedSelectTodo(TodoSelectRequest request) {

        return CachingStrategy.cachingIfEmpty(request,
                (req) -> todoListRepository.findByGroupInfoIdAndDate(req.getGroupId(), req.getDate()),
                (req) -> {
                    List<TodoResponse> todoResponseList = selectTodo(req);

                    return todoListRepository.save(TodoList.builder() // List<TodoResponse> => TodoList 후 save
                            .groupInfoId(req.getGroupId())
                            .date(req.getDate())
                            .todoList(todoResponseList.stream()
                                    .map(todo -> Todo.builder()
                                            .id(todo.getId())
                                            .houseworkMembers(todo.getHouseworkMembers().stream()
                                                    .map(u -> TodoUser.builder()
                                                            .name(u.getName())
                                                            .email(u.getEmail())
                                                            .profileImageUrl(u.getProfileImageUrl())
                                                            .build())
                                                    .collect(Collectors.toList()))
                                            .category(todo.getCategory())
                                            .title(todo.getTitle())
                                            .detail(todo.getDetail())
                                            .status(todo.getStatus().name())
                                            .date(todo.getDate())
                                            .endTime(todo.getEndTime())
                                            .build())
                                    .collect(Collectors.toList()))
                            .build());
                },
                (todo) -> todo.isDirtyBit() == false); // dirtyBit가 false가 아니면 캐시 업데이트
    }

    // GroupInfoId와 Date를 통해 Todo List 가져오기
    public List<TodoResponse> selectTodo(TodoSelectRequest request) {
        Long groupId = request.getGroupId();
        LocalDate date = request.getDate();

        // Map<HOUSEWORK_INFO_ID, HOUSEWORK_TODO>
        Map<Long, HouseworkTodo> todoMap = houseworkTodoRepository.findByGroupInfoAndDate(groupId, date).stream()
                .collect(Collectors.toMap(todo -> todo.getHouseworkInfo().getId(), todo -> todo));

        // 그룹의 HouseworkInfo 조회 후 필터링해서 추가해야하는 TodoList를 만든다.
        List<HouseworkTodo> insertTodoList = houseworkInfoRepository.findHouseworkInfoByGroupInfoId(groupId).stream()
                .filter(info -> !todoMap.containsKey(info.getId())) // HouseworkTodo에 이미 있는 INFO는 제외
                .filter(info -> // PERIOD에 맞는 INFO 선별
                        switch (info.getType()) {
                            case HOUSEWORK_PERIOD_EVERYDAY -> true;
                            case HOUSEWORK_PERIOD_WEEK -> DayOfWeekUtils.equals(info.getWeekDate(), date);
                            case HOUSEWORK_PERIOD_MONTH -> Arrays.asList(info.getMonthDate().split(",")).stream()
                                    .filter(d -> Integer.parseInt(d) == date.getDayOfMonth())
                                    .findFirst().isPresent();
                            default -> false;
                        })
                .map(info -> HouseworkTodo.makeTodoReferInfo(info, info.getGroupInfo(), date))
                .collect(Collectors.toList());

        houseworkTodoRepository.saveAll(insertTodoList); // Todo 저장

        // Map<GROUP_MEMBER_ID, USER>
        Map<Long, User> userMap = groupMemberRepository.findGroupMemberByGroupId(groupId).stream()
                .collect(Collectors.toMap(gm -> gm.getId(), gm -> gm.getUser()));

        return houseworkTodoRepository.findByGroupInfoAndDate(groupId, date).stream() // 최신 Todo 불러와 리턴
                .map(todo -> TodoResponse.of(todo, userMap))
                .collect(Collectors.toList());
    }
}

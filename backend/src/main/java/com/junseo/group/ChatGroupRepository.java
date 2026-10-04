package com.junseo.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ChatGroupRepository extends JpaRepository<ChatGroup, Long> {

    /** Groups whose last member left (e.g. their account was deleted). */
    @Modifying
    @Query(nativeQuery = true, value = "delete from chat_groups g where not exists (select 1 from chat_group_members m where m.group_id = g.id)")
    int deleteEmpty();
}

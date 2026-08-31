package com.xiqin.modules.notification.repository;

import com.xiqin.modules.notification.entity.SystemMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SystemMessageRepository extends JpaRepository<SystemMessage, Long> {
    @Query("SELECT m FROM SystemMessage m WHERE " +
           "((:isAdmin = true AND (m.recipientUserId IS NULL OR m.recipientUserId = :viewerId)) " +
           "OR (:isAdmin = false AND m.recipientUserId = :viewerId)) AND " +
           "(:type IS NULL OR m.messageType = :type) AND (:unread IS NULL OR m.isRead = false) AND " +
           "(:keyword IS NULL OR LOWER(m.title) LIKE :keyword OR LOWER(COALESCE(m.content, '')) LIKE :keyword " +
           "OR LOWER(COALESCE(m.createdByName, '')) LIKE :keyword) ORDER BY m.createdAt DESC")
    Page<SystemMessage> searchVisible(@Param("viewerId") Long viewerId, @Param("isAdmin") boolean isAdmin,
                               @Param("keyword") String keyword, @Param("type") String type,
                               @Param("unread") Boolean unread, Pageable pageable);

    @Query("SELECT COUNT(m) FROM SystemMessage m WHERE m.isRead = false AND " +
           "((:isAdmin = true AND (m.recipientUserId IS NULL OR m.recipientUserId = :viewerId)) " +
           "OR (:isAdmin = false AND m.recipientUserId = :viewerId))")
    long countVisibleUnread(@Param("viewerId") Long viewerId, @Param("isAdmin") boolean isAdmin);

    @Modifying
    @Query("UPDATE SystemMessage m SET m.isRead = true WHERE m.isRead = false AND " +
           "((:isAdmin = true AND (m.recipientUserId IS NULL OR m.recipientUserId = :viewerId)) " +
           "OR (:isAdmin = false AND m.recipientUserId = :viewerId))")
    int markAllVisibleRead(@Param("viewerId") Long viewerId, @Param("isAdmin") boolean isAdmin);
}

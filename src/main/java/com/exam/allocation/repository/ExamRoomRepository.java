package com.exam.allocation.repository;

import com.exam.allocation.model.ExamRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ExamRoomRepository extends JpaRepository<ExamRoom, Long> {
    List<ExamRoom> findByExamId(Long examId);
    List<ExamRoom> findByRoomId(Long roomId);
    @Transactional
    void deleteByExamId(Long examId);
}

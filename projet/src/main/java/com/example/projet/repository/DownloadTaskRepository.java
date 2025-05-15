package com.example.projet.repository;

import com.example.projet.model.DownloadTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DownloadTaskRepository extends JpaRepository<DownloadTask, Long> {



}
package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.NewsRequest;
import com.zelvira.zelvira.entity.News;
import com.zelvira.zelvira.exception.ResourceNotFoundException;
import com.zelvira.zelvira.repository.NewsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.zelvira.zelvira.entity.User;
import com.zelvira.zelvira.repository.UserRepository;

@Service
public class NewsService {

    private final NewsRepository newsRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NewsService(NewsRepository newsRepository, NotificationService notificationService,
            UserRepository userRepository) {
        this.newsRepository = newsRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @Transactional
    public News createNews(NewsRequest request) {
        News news = News.builder()
                .title(request.getTitle())
                .slug(request.getSlug())
                .content(request.getContent())
                .imageUrl(request.getImageUrl())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .targetUserId(request.getTargetUserId())
                .build();
        News saved = newsRepository.save(news);

        // Trigger notification
        if (Boolean.TRUE.equals(saved.getIsActive())) {
            if (request.getTargetUserId() != null) {
                userRepository.findById(request.getTargetUserId()).ifPresent(user -> {
                    notificationService.notifyPersonalNews(user, saved.getTitle(), saved.getContent(), saved.getId(),
                            saved.getSlug());
                });
            } else {
                notificationService.notifyBroadcastNews(saved.getTitle(), saved.getContent(), saved.getId(),
                        saved.getSlug());
            }
        }

        return saved;
    }

    public List<News> getAllNews() {
        return newsRepository.findAll();
    }

    public News getNewsById(Long id) {
        return newsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("News not found with id: " + id));
    }

    public News getNewsBySlug(String slug) {
        return newsRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("News not found with slug: " + slug));
    }

    @Transactional
    public News updateNews(Long id, NewsRequest request) {
        News news = getNewsById(id);
        news.setTitle(request.getTitle());
        news.setSlug(request.getSlug());
        news.setContent(request.getContent());
        news.setImageUrl(request.getImageUrl());
        news.setTargetUserId(request.getTargetUserId());
        if (request.getIsActive() != null) {
            news.setIsActive(request.getIsActive());
        }
        return newsRepository.save(news);
    }

    @Transactional
    public void deleteNews(Long id) {
        if (!newsRepository.existsById(id)) {
            throw new ResourceNotFoundException("News not found with id: " + id);
        }
        newsRepository.deleteById(id);
    }

    public void resendNotification(Long id) {
        News saved = getNewsById(id);
        if (Boolean.TRUE.equals(saved.getIsActive())) {
            if (saved.getTargetUserId() != null) {
                userRepository.findById(saved.getTargetUserId()).ifPresent(user -> {
                    notificationService.notifyPersonalNews(user, saved.getTitle(), saved.getContent(), saved.getId(),
                            saved.getSlug());
                });
            } else {
                notificationService.notifyBroadcastNews(saved.getTitle(), saved.getContent(), saved.getId(),
                        saved.getSlug());
            }
        }
    }
}

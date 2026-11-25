package com.zelvira.zelvira.scheduler;

import com.zelvira.zelvira.entity.ContentSection;
import com.zelvira.zelvira.repository.ContentSectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Slf4j
public class FlashSaleScheduler {

    private final ContentSectionRepository sectionRepository;

    public FlashSaleScheduler(ContentSectionRepository sectionRepository) {
        this.sectionRepository = sectionRepository;
    }

    /**
     * Runs every minute to check and deactivate expired flash sales.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void deactivateExpiredFlashSales() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        List<ContentSection> activeFlashSales = sectionRepository.findAll().stream()
                .filter(s -> "FLASH_SALE_WEB".equals(s.getType()) && s.getIsActive())
                .toList();

        for (ContentSection section : activeFlashSales) {
            LocalDateTime endDate = section.getEndDate();
            if (endDate != null) {
                if (now.isAfter(endDate)) {
                    log.info("Deactivating expired Flash Sale: {}", section.getTitle());
                    section.setIsActive(false);
                    sectionRepository.save(section);
                }
            }
        }
    }
}

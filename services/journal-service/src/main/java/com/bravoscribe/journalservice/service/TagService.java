package com.bravoscribe.journalservice.service;

import com.bravoscribe.journalservice.dto.CreateTagRequest;
import com.bravoscribe.journalservice.dto.TagResponse;
import com.bravoscribe.journalservice.entity.Tag;
import com.bravoscribe.journalservice.exception.JournalError;
import com.bravoscribe.journalservice.exception.JournalServiceException;
import com.bravoscribe.journalservice.repository.TagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TagService {

    private static final Logger log = LoggerFactory.getLogger(TagService.class);

    private final TagRepository tagRepo;

    public TagService(TagRepository tagRepo) {
        this.tagRepo = tagRepo;
    }

    public TagResponse createTag(UUID userId, CreateTagRequest req) {
        if (tagRepo.existsByUserIdAndNameIgnoreCase(userId, req.name())) {
            throw new JournalServiceException(new JournalError.DuplicateTagName(req.name()));
        }
        var tag = new Tag(userId, req.name());
        return TagResponse.from(tagRepo.save(tag));
    }

    @Transactional(readOnly = true)
    public List<TagResponse> listTags(UUID userId) {
        return tagRepo.findByUserIdOrderByNameAsc(userId)
                .stream()
                .map(TagResponse::from)
                .toList();
    }

    public void deleteTag(UUID userId, UUID tagId) {
        var tag = tagRepo.findByIdAndUserId(tagId, userId)
                .orElseThrow(() -> new JournalServiceException(new JournalError.TagNotFound()));
        tagRepo.delete(tag);
    }
}

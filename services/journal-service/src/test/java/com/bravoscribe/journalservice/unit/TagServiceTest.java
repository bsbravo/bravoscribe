package com.bravoscribe.journalservice.unit;

import com.bravoscribe.journalservice.dto.CreateTagRequest;
import com.bravoscribe.journalservice.dto.TagResponse;
import com.bravoscribe.journalservice.entity.Tag;
import com.bravoscribe.journalservice.exception.JournalError;
import com.bravoscribe.journalservice.exception.JournalServiceException;
import com.bravoscribe.journalservice.repository.TagRepository;
import com.bravoscribe.journalservice.service.TagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock TagRepository tagRepo;

    TagService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TAG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TagService(tagRepo);
    }

    @Test
    void createTag_success() {
        when(tagRepo.existsByUserIdAndNameIgnoreCase(USER_ID, "learning")).thenReturn(false);
        when(tagRepo.save(any())).thenAnswer(inv -> {
            Tag t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "id", TAG_ID);
            return t;
        });

        var result = service.createTag(USER_ID, new CreateTagRequest("learning"));

        assertThat(result.id()).isEqualTo(TAG_ID);
        assertThat(result.name()).isEqualTo("learning");
    }

    @Test
    void createTag_duplicate_throws_409() {
        when(tagRepo.existsByUserIdAndNameIgnoreCase(USER_ID, "work")).thenReturn(true);

        assertThatThrownBy(() -> service.createTag(USER_ID, new CreateTagRequest("work")))
                .isInstanceOf(JournalServiceException.class)
                .satisfies(ex -> assertThat(((JournalServiceException) ex).getError())
                        .isInstanceOf(JournalError.DuplicateTagName.class));
    }

    @Test
    void listTags_returns_alphabetical_order() {
        var tagA = makeTag("apple");
        var tagB = makeTag("banana");
        when(tagRepo.findByUserIdOrderByNameAsc(USER_ID)).thenReturn(List.of(tagA, tagB));

        List<TagResponse> result = service.listTags(USER_ID);

        assertThat(result).extracting(TagResponse::name).containsExactly("apple", "banana");
    }

    @Test
    void deleteTag_success() {
        var tag = makeTag("work");
        when(tagRepo.findByIdAndUserId(TAG_ID, USER_ID)).thenReturn(Optional.of(tag));

        service.deleteTag(USER_ID, TAG_ID);

        verify(tagRepo).delete(tag);
    }

    @Test
    void deleteTag_not_found_throws_404() {
        when(tagRepo.findByIdAndUserId(TAG_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTag(USER_ID, TAG_ID))
                .isInstanceOf(JournalServiceException.class)
                .satisfies(ex -> assertThat(((JournalServiceException) ex).getError())
                        .isInstanceOf(JournalError.TagNotFound.class));
    }

    private Tag makeTag(String name) {
        var tag = new Tag(USER_ID, name);
        ReflectionTestUtils.setField(tag, "id", UUID.randomUUID());
        return tag;
    }
}

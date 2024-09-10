package com.github.simple_mocks.content.embedded.service;

import com.github.simple_mocks.content.api.condition.*;
import com.github.simple_mocks.content.api.dto.ContentHolder;
import com.github.simple_mocks.content.api.rq.GetContentRq;
import com.github.simple_mocks.content.api.rs.GetContentRs;
import com.github.simple_mocks.content.api.service.ContentService;
import com.github.simple_mocks.content.embedded.codec.ContentCodec;
import com.github.simple_mocks.content.embedded.conf.ContentServiceEmbeddedCondition;
import com.github.simple_mocks.content.embedded.entity.AttributeEntity;
import com.github.simple_mocks.content.embedded.entity.ContentEntity;
import com.github.simple_mocks.content.embedded.exception.NotFoundException;
import com.github.simple_mocks.content.embedded.exception.NotSupportedException;
import com.github.simple_mocks.content.embedded.repository.AttributeRepository;
import com.github.simple_mocks.content.embedded.repository.ContentGroupRepository;
import com.github.simple_mocks.content.embedded.repository.ContentRepository;
import com.github.simple_mocks.content.embedded.repository.SystemRepository;
import com.github.simple_mocks.content.mutable.api.rq.*;
import com.github.simple_mocks.content.mutable.api.service.MutableContentService;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author sibmaks
 * @since 0.0.1
 */
@Component
@RequiredArgsConstructor
@Conditional(ContentServiceEmbeddedCondition.class)
public class ContentServiceEmbedded implements ContentService {
    private final AttributeRepository attributeRepository;
    private final ContentRepository contentRepository;
    private final ContentGroupRepository contentGroupRepository;
    private final ContentCodec codec;

    @Override
    public <T> GetContentRs<T> getContent(@Nonnull GetContentRq<T> rq) {
        var contentGroup = contentGroupRepository.findBySystem_CodeAndTypeAndCode(
                rq.systemCode(),
                rq.type(),
                rq.groupCode()
        ).orElseThrow(() -> new NotFoundException("Content group not found"));

        var conditions = rq.conditions();

        var rsContents = new HashMap<String, ContentHolder<T>>();

        var contents = contentRepository.findAllByGroup(contentGroup);
        for (var content : contents) {
            var attributes = attributeRepository.findAllByContentId(content.getId())
                    .stream()
                    .filter(it -> it.getValue() != null)
                    .collect(Collectors.toMap(AttributeEntity::getCode, AttributeEntity::getValue));

            var allMet = conditions == null || checkConditions(conditions, attributes);

            if (!allMet) {
                continue;
            }
            var decoded = codec.decode(content.getContent(), rq.contentType());

            var contentCode = content.getCode();

            var contentHolder = ContentHolder.<T>builder()
                    .code(contentCode)
                    .content(decoded)
                    .attributes(attributes)
                    .build();

            rsContents.put(contentCode, contentHolder);
        }

        return new GetContentRs<>(rsContents);
    }

    private boolean checkConditions(List<Condition> conditions, Map<String, String> attributes) {
        for (var condition : conditions) {
            switch (condition) {
                case EqualsCondition equalsCondition -> {
                    var value = attributes.get(equalsCondition.getAttribute());
                    if (!Objects.equals(value, equalsCondition.getValue())) {
                        return false;
                    }
                }
                case NotEqualsCondition notEqualsCondition -> {
                    var value = attributes.get(notEqualsCondition.getAttribute());
                    if (Objects.equals(value, notEqualsCondition.getValue())) {
                        return false;
                    }
                }
                case IsNullCondition isNullCondition -> {
                    var value = attributes.get(isNullCondition.getAttribute());
                    if (value != null) {
                        return false;
                    }
                }
                case NotNullCondition notNullCondition -> {
                    var value = attributes.get(notNullCondition.getAttribute());
                    if (value == null) {
                        return false;
                    }
                }
                default -> throw new NotSupportedException("Unknown condition type");
            }
        }
        return true;
    }

}

package com.pancakeswap.nft.publish.service;

import com.pancakeswap.nft.publish.model.dto.AttributeDto;
import com.pancakeswap.nft.publish.model.dto.TokenDataDto;
import com.pancakeswap.nft.publish.model.entity.*;
import com.pancakeswap.nft.publish.model.entity.Collection;
import com.pancakeswap.nft.publish.repository.*;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DBService {

    @Value("${nft.collection.address}")
    private String contract;
    @Value("${nft.collection.name}")
    private String name;
    @Value("${nft.collection.description}")
    private String description;
    @Value("${nft.collection.symbol}")
    private String symbol;
    @Value("${nft.collection.owner}")
    private String owner;

    private final CollectionRepository collectionRepository;
    private final AttributeRepository attributeRepository;
    private final TokenRepository tokenRepository;
    private final MetadataRepository metadataRepository;

    private final Map<String, String> attributesMapCache = Collections.synchronizedMap(new HashMap<>());

    public DBService(CollectionRepository collectionRepository, AttributeRepository attributeRepository, TokenRepository tokenRepository, MetadataRepository metadataRepository) {
        this.collectionRepository = collectionRepository;
        this.attributeRepository = attributeRepository;
        this.tokenRepository = tokenRepository;
        this.metadataRepository = metadataRepository;
    }

    public Collection storeCollection(Integer totalSupply) {
        Collection collection = collectionRepository.findByAddress(contract.toLowerCase(Locale.ROOT));
        if (collection != null) {
            return collection;
        }

        collection = new Collection();
        collection.setAddress(contract.toLowerCase(Locale.ROOT));
        collection.setOwner(owner.toLowerCase(Locale.ROOT));
        collection.setName(name);
        collection.setDescription(description);
        collection.setSymbol(symbol);
        collection.setTotalSupply(totalSupply);
        collection.setVerified(false);
        collection.setVisible(false);
        collection.setCreatedAt(new Date());
        collection.setUpdatedAt(new Date());

        return collectionRepository.save(collection);
    }

    public Token getExistedExist(String collectionId, String tokenId) {
        return tokenRepository.findByParentCollectionAndTokenId(new ObjectId(collectionId), tokenId);
    }

    public String getTokenName(String metadataId) {
        return metadataRepository.findById(metadataId).get().getName();
    }

    @Transactional
    public void storeToken(String collectionId, TokenDataDto tokenDataDto) {
        List<ObjectId> attributes = storeAttributes(collectionId, tokenDataDto.getAttributes());

        Token token = tokenRepository.findByParentCollectionAndTokenId(new ObjectId(collectionId), tokenDataDto.getTokenId());
        if (token == null) {
            token = new Token();
            token.setParentCollection(new ObjectId(collectionId));
            token.setCreatedAt(new Date());
            token.setUpdatedAt(new Date());
        }

        if (token.getMetadata() == null) {
            Metadata metadata = storeMetadata(tokenDataDto, collectionId);
            token.setMetadata(new ObjectId(metadata.getId()));
        }

        token.setTokenId(tokenDataDto.getTokenId());
        token.setBurned(false);
        token.setAttributes(attributes);

        tokenRepository.save(token);
    }

    @Transactional
    public void deleteCollection(String id) {
        tokenRepository.deleteAllByParentCollection(new ObjectId(id));
        metadataRepository.deleteAllByParentCollection(new ObjectId(id));
        attributeRepository.deleteAllByParentCollection(new ObjectId(id));

        collectionRepository.deleteById(id);
    }

    public void deleteAttributes(String id) {
        attributeRepository.deleteAllByParentCollection(new ObjectId(id));
    }

    private List<ObjectId> storeAttributes(String collectionId, List<AttributeDto> attributes) {
        synchronized (attributesMapCache) {
            List<AttributeDto> toStore = attributes.stream().filter(item -> {
                boolean inCache = attributesMapCache.get(String.format("%s-%s-%s", collectionId, item.getTraitType(), item.getValue())) != null;
                if (inCache) {
                    return false;
                } else {
                    Optional<Attribute> attr = attributeRepository.findByParentCollectionAndTraitTypeAndValue(new ObjectId(collectionId), item.getTraitType(), item.getValue());
                    attr.ifPresent((a) -> attributesMapCache.put(String.format("%s-%s-%s", collectionId, a.getTraitType(), a.getValue()), a.getId()));

                    return attr.isEmpty();
                }
            }).collect(Collectors.toList());

            List<Attribute> entities = toStore.stream().map(attributeDto -> {
                Attribute attribute = new Attribute();
                attribute.setParentCollection(new ObjectId(collectionId));
                attribute.setTraitType(attributeDto.getTraitType());
                attribute.setValue(attributeDto.getValue());

                attribute.setCreatedAt(new Date());
                attribute.setUpdatedAt(new Date());

                return attribute;
            }).collect(Collectors.toList());

            List<Attribute> stored = attributeRepository.saveAll(entities);
            stored.forEach(a -> attributesMapCache.put(String.format("%s-%s-%s", collectionId, a.getTraitType(), a.getValue()), a.getId()));

            return stored.stream().map(a -> new ObjectId(a.getId())).collect(Collectors.toList());
        }
    }

    private Metadata storeMetadata(TokenDataDto dto, String parentId) {
        Metadata metadata = new Metadata();
        metadata.setParentCollection(new ObjectId(parentId));
        metadata.setName(dto.getName());
        metadata.setDescription(dto.getDescription());
        metadata.setGif(false);
        metadata.setMp4(false);
        metadata.setWebm(false);
        metadata.setCreatedAt(new Date());
        metadata.setUpdatedAt(new Date());

        return metadataRepository.save(metadata);
    }
}
/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database.repository;

import java.util.Optional;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The base of every Kiln repository: only the operations Kiln allows, each needing the caller's
 * transaction (DB-11). A transaction annotation on a repository doesn't reach the methods Spring's
 * {@code CrudRepository} declares, so those are redeclared here instead, and bulk operations such
 * as {@code saveAll} and {@code deleteAll} don't exist.
 *
 * <p>{@link #save} only inserts: entities with assigned IDs implement {@code Persistable}, and every
 * update is a named {@code @Modifying @Query} setting only the columns the runtime role may change.
 *
 * @param <T> the entity
 * @param <I> its ID
 */
@NoRepositoryBean
@Transactional(propagation = Propagation.MANDATORY)
public interface KilnRepository<T, I> extends Repository<T, I> {

    <S extends T> S save(S entity);

    Optional<T> findById(I id);

    void deleteById(I id);
}

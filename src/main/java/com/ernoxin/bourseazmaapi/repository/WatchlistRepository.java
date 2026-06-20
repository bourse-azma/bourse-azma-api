package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.Watchlist;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    @EntityGraph(attributePaths = "symbols")
    List<Watchlist> findDistinctByUserIdOrderByIdAsc(Long userId);

    @EntityGraph(attributePaths = "symbols")
    Optional<Watchlist> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);
}

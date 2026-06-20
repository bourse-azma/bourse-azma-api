package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.WatchlistSymbol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WatchlistSymbolRepository extends JpaRepository<WatchlistSymbol, Long> {

    boolean existsByWatchlistIdAndSymbolKey(Long watchlistId, String symbolKey);

    Optional<WatchlistSymbol> findByIdAndWatchlistId(Long id, Long watchlistId);
}

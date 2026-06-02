package com.ernoxin.boorsazmaapi.service;

import com.ernoxin.boorsazmaapi.dto.WatchlistCreateRequest;
import com.ernoxin.boorsazmaapi.dto.WatchlistResponse;
import com.ernoxin.boorsazmaapi.dto.WatchlistSymbolCreateRequest;
import com.ernoxin.boorsazmaapi.dto.WatchlistSymbolResponse;
import com.ernoxin.boorsazmaapi.dto.WatchlistUpdateRequest;
import com.ernoxin.boorsazmaapi.exception.DuplicateResourceException;
import com.ernoxin.boorsazmaapi.exception.ResourceNotFoundException;
import com.ernoxin.boorsazmaapi.model.Watchlist;
import com.ernoxin.boorsazmaapi.model.WatchlistSymbol;
import com.ernoxin.boorsazmaapi.repository.UserRepository;
import com.ernoxin.boorsazmaapi.repository.WatchlistRepository;
import com.ernoxin.boorsazmaapi.repository.WatchlistSymbolRepository;
import com.ernoxin.boorsazmaapi.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WatchlistServiceImpl implements WatchlistService {

    private final UserRepository userRepository;
    private final WatchlistRepository watchlistRepository;
    private final WatchlistSymbolRepository watchlistSymbolRepository;

    @Override
    @Transactional(readOnly = true)
    public List<WatchlistResponse> getCurrentUserWatchlists() {
        Long userId = SecurityUtils.currentUserId();
        return watchlistRepository.findDistinctByUserIdOrderByIdAsc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public WatchlistResponse create(WatchlistCreateRequest request) {
        Long userId = SecurityUtils.currentUserId();
        String normalizedName = normalizeRequiredText(request.getName());

        if (watchlistRepository.existsByUserIdAndNameIgnoreCase(userId, normalizedName)) {
            throw new DuplicateResourceException("این نام دیده بان قبلا ثبت شده است.");
        }

        Watchlist watchlist = new Watchlist();
        watchlist.setName(normalizedName);
        watchlist.setUser(userRepository.getReferenceById(userId));

        return toDto(watchlistRepository.save(watchlist));
    }

    @Override
    @Transactional
    public WatchlistResponse update(Long watchlistId, WatchlistUpdateRequest request) {
        Long userId = SecurityUtils.currentUserId();
        String normalizedName = normalizeRequiredText(request.getName());
        Watchlist watchlist = findUserWatchlistById(watchlistId, userId);

        if (watchlistRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, normalizedName, watchlistId)) {
            throw new DuplicateResourceException("این نام دیده بان قبلا ثبت شده است.");
        }

        watchlist.setName(normalizedName);
        return toDto(watchlistRepository.save(watchlist));
    }

    @Override
    @Transactional
    public void delete(Long watchlistId) {
        Long userId = SecurityUtils.currentUserId();
        Watchlist watchlist = findUserWatchlistById(watchlistId, userId);
        watchlistRepository.delete(watchlist);
    }

    @Override
    @Transactional
    public WatchlistResponse addSymbol(Long watchlistId, WatchlistSymbolCreateRequest request) {
        Long userId = SecurityUtils.currentUserId();
        Watchlist watchlist = findUserWatchlistById(watchlistId, userId);

        String symbolKey = normalizeRequiredText(request.getSymbolKey());
        if (watchlistSymbolRepository.existsByWatchlistIdAndSymbolKey(watchlist.getId(), symbolKey)) {
            throw new DuplicateResourceException("این نماد قبلا در دیده بان ثبت شده است.");
        }

        WatchlistSymbol symbol = new WatchlistSymbol();
        symbol.setWatchlist(watchlist);
        symbol.setSymbolKey(symbolKey);
        symbol.setSymbol(normalizeRequiredText(request.getSymbol()));
        symbol.setName(normalizeRequiredText(request.getName()));
        symbol.setSourceType(normalizeOptionalText(request.getSourceType()));
        symbol.setInstrumentCode(normalizeOptionalText(request.getInstrumentCode()));
        symbol.setIsin(normalizeOptionalText(request.getIsin()));
        watchlist.getSymbols().add(symbol);

        return toDto(watchlistRepository.save(watchlist));
    }

    private Watchlist findUserWatchlistById(Long watchlistId, Long userId) {
        return watchlistRepository.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("دیده بان مورد نظر یافت نشد."));
    }

    private WatchlistResponse toDto(Watchlist watchlist) {
        List<WatchlistSymbolResponse> symbols = watchlist.getSymbols().stream()
                .map(symbol -> new WatchlistSymbolResponse(
                        symbol.getId(),
                        symbol.getSymbolKey(),
                        symbol.getSymbol(),
                        symbol.getName(),
                        symbol.getSourceType(),
                        symbol.getInstrumentCode(),
                        symbol.getIsin()
                ))
                .toList();

        return new WatchlistResponse(watchlist.getId(), watchlist.getName(), symbols);
    }

    private String normalizeRequiredText(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeOptionalText(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

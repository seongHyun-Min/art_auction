package com.example.artaction.service;

import com.example.artaction.contant.AuctionStatus;
import com.example.artaction.domain.entity.Auction;
import com.example.artaction.domain.entity.ArtWork;
import com.example.artaction.domain.repository.AuctionRepository;
import com.example.artaction.domain.repository.ArtWorkRepository;
import com.example.artaction.domain.repository.BidRepository;
import com.example.artaction.dto.auction.AuctionResponseDto;
import com.example.artaction.dto.auction.PostAuctionRequestDto;
import com.example.artaction.exception.auction.NotFoundAuctionException;
import com.example.artaction.exception.auction.NotSaveAuctionException;
import com.example.artaction.exception.artwork.NotFoundArtWorkException;
import com.example.artaction.exception.artwork.NotSaveArtWorkException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionService {
    private final AuctionRepository auctionRepository;
    private final ArtWorkRepository artWorkRepository;
    private final BidRepository bidRepository;
    private final RedisCacheService redisCacheService;

    @Transactional
    public Long post(PostAuctionRequestDto requestDto) {
        ArtWork artWork = findArtWorkById(requestDto.getArtWorkId());
        Auction auction = requestDto.toEntity(artWork);
        try {
            return auctionRepository.save(auction).getId();
        } catch (NotSaveArtWorkException e) {
            throw new NotSaveAuctionException("경매 등록에 실패 하였습니다");
        }
    }

    @Transactional(readOnly = true)
    public List<AuctionResponseDto> findByArtWork(Long artWorkId) {
        ArtWork artWork = findByArtWorkId(artWorkId);
        long currentPrice = getCurrentPrice(artWork.getAuction().getId());

        return auctionRepository.findByArtWork(artWork)
                .stream()
                .map(auction -> auction.from(currentPrice))
                .toList();
    }

    @Scheduled(fixedRate = 3600000) // 1시간마다 실행 (3600000ms = 1시간)
    public void updateActionStatus() {
        LocalDateTime currentTime = LocalDateTime.now();
        List<Auction> startAuctions = auctionRepository.findByStatusAndStartTimeAfter(AuctionStatus.PREPARE,
                currentTime);
        List<Auction> endAuctions = auctionRepository.findByStatusAndEndTimeAfter(AuctionStatus.START, currentTime);

        for (Auction auction : startAuctions) {
            Auction startAuction = getBuild(auction, AuctionStatus.START, auction.getCurrentPrice());
            auctionRepository.save(startAuction);
        }

        for (Auction auction : endAuctions) {
            Optional<Integer> maxPrice = bidRepository.findTop1ByAuctionOrderByPriceDesc(auction);
            AuctionStatus auctionStatus = maxPrice.isPresent() ? AuctionStatus.END : AuctionStatus.FAIL;
            long endPrice = maxPrice.orElse(0).longValue();

            Auction endAuction = getBuild(auction, auctionStatus, endPrice);
            auctionRepository.save(endAuction);
        }
    }

    @Transactional(readOnly = true)
    public AuctionResponseDto findById(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new NotFoundAuctionException("아이디와 일치하는 경매를 찾을수 없습니다"));

        long currentPrice = getCurrentPrice(auctionId);
        return auction.from(currentPrice);
    }


    @Cacheable(key = "#auctionId", value = "bidPrice")
    public long getCurrentPrice(Long auctionId) {
        long cacheBidPrice = redisCacheService.getCurrentBidPrice(auctionId);
        if (cacheBidPrice == 0L) {
            //캐시 값이 없다면 DB 조회후 캐시로 업데이트
            Optional<Auction> auctionOptional = auctionRepository.findById(auctionId);
            cacheBidPrice = auctionOptional.map(Auction::getCurrentPrice).orElse(0L);
            redisCacheService.updateBidPriceToRedis(auctionId, cacheBidPrice);
        }

        return cacheBidPrice;
    }

    private static Auction getBuild(Auction auction, AuctionStatus status, long price) {
        return Auction.builder()
                .artWork(auction.getArtWork())
                .startTime(auction.getStartTime())
                .endTime(auction.getEndTime())
                .startingPrice(auction.getStartingPrice())
                .currentPrice(price)
                .status(status)
                .build();
    }

    private ArtWork findByArtWorkId(Long artWorkId) {
        return artWorkRepository.findById(artWorkId)
                .orElseThrow(() -> new NotFoundArtWorkException("아이디와 일치하는 물품을 찾을 수 없습니다"));
    }

    private ArtWork findArtWorkById(Long artWorkId) {
        return artWorkRepository.findById(artWorkId)
                .orElseThrow(() -> new NotFoundArtWorkException("아이디와 일치하는 물품을 찾을 수 없습니다"));
    }
}

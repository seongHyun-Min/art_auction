package com.example.artaction.domain.entity;


import com.example.artaction.dto.bid.AuctionBidResponseDto;
import com.example.artaction.dto.bid.UserBidResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.*;
import java.time.LocalDateTime;

@Slf4j
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "bid")
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bid_id")
    private Long id;

    @Column(name = "bid_price")
    private long price;

    @Column(name = "bid_time")
    private LocalDateTime bidTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id")
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public AuctionBidResponseDto fromAuction() {
        return AuctionBidResponseDto.builder()
                .userName(user.getName())
                .price(price)
                .bidTime(bidTime)
                .build();
    }

    public UserBidResponseDto fromUser() {
        return UserBidResponseDto.builder()
                .price(price)
                .bidTime(bidTime)
                .auctionId(auction.getId())
                .auctionStatus(auction.getStatus())
                .build();
    }
}

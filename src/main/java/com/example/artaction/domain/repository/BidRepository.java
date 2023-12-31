package com.example.artaction.domain.repository;

import com.example.artaction.domain.entity.Auction;
import com.example.artaction.domain.entity.Bid;
import com.example.artaction.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {
    Optional<Bid> findById(Long id);

    List<Bid> findByUser(User user);

    @Query("SELECT b FROM Bid b WHERE b.auction = :auction ORDER BY b.bidTime DESC")
    List<Bid> findTop5ByAuctionOrderByBidTimeDesc(@Param("auction") Auction auction);

    Optional<Bid> findByAuctionAndUser(Auction auction, User user);

    Optional<Integer> findTop1ByAuctionOrderByPriceDesc(Auction auction);
}

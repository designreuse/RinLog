/*
 * Copyright (C) 2013-2015 Rinde van Lon, iMinds DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rinde.logistics.pdptw.mas.comm;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Sets.newLinkedHashSet;
import static java.util.Collections.unmodifiableSet;

import java.util.Collection;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.ParcelState;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.event.EventDispatcher;
import com.github.rinde.rinsim.event.Listener;
import com.github.rinde.rinsim.pdptw.common.PDPRoadModel;
import com.google.common.base.Optional;

/**
 * Basic implementation for {@link Bidder}.
 * @author Rinde van Lon
 */
public abstract class AbstractBidder implements Bidder {

  /**
   * The logger.
   */
  protected static final Logger LOGGER = LoggerFactory
    .getLogger(AbstractBidder.class);

  /**
   * The set of parcels that are assigned to this bidder.
   */
  protected final Set<Parcel> assignedParcels;

  /**
   * The set of parcels that are claimed by this bidder.
   */
  protected final Set<Parcel> claimedParcels;

  /**
   * The event dispatcher.
   */
  protected final EventDispatcher eventDispatcher;

  /**
   * The road model.
   */
  protected Optional<PDPRoadModel> roadModel;

  /**
   * The pdp model.
   */
  protected Optional<PDPModel> pdpModel;

  /**
   * The vehicle for which this bidder operates.
   */
  protected Optional<Vehicle> vehicle;

  /**
   * Initializes bidder.
   */
  public AbstractBidder() {
    assignedParcels = newLinkedHashSet();
    claimedParcels = newLinkedHashSet();
    eventDispatcher = new EventDispatcher(CommunicatorEventType.values());
    roadModel = Optional.absent();
    pdpModel = Optional.absent();
    vehicle = Optional.absent();
  }

  @Override
  public void addUpdateListener(Listener l) {
    eventDispatcher.addListener(l, CommunicatorEventType.CHANGE);
  }

  // ignore
  @Override
  public void waitFor(Parcel p) {}

  @Override
  public void claim(Parcel p) {
    LOGGER.info("claim {}", p);
    checkArgument(!claimedParcels.contains(p),
      "Can not claim parcel %s because it is already claimed.", p);
    checkArgument(assignedParcels.contains(p),
      "Can not claim parcel %s which is not in assigned parcels: %s.", p,
      assignedParcels);
    checkArgument(pdpModel.get().getParcelState(p) == ParcelState.AVAILABLE
      || pdpModel.get().getParcelState(p) == ParcelState.ANNOUNCED);
    checkArgument(claimedParcels.isEmpty(),
      "claimed parcels must be empty, is %s.", claimedParcels);
    claimedParcels.add(p);
    LOGGER.info(" > assigned parcels {}", assignedParcels);
    LOGGER.info(" > claimed parcels {}", claimedParcels);
  }

  @Override
  public void unclaim(Parcel p) {
    LOGGER.info("unclaim {}", p);
    checkArgument(claimedParcels.contains(p),
      "Can not unclaim %s because it is not claimed.", p);
    checkArgument(pdpModel.get().getParcelState(p) == ParcelState.AVAILABLE
      || pdpModel.get().getParcelState(p) == ParcelState.ANNOUNCED);
    claimedParcels.remove(p);
  }

  @Override
  public void done() {
    LOGGER.info("done {}", claimedParcels);
    assignedParcels.removeAll(claimedParcels);
    claimedParcels.clear();
  }

  @Override
  public final Collection<Parcel> getParcels() {
    return unmodifiableSet(assignedParcels);
  }

  @Override
  public final Collection<Parcel> getClaimedParcels() {
    return unmodifiableSet(claimedParcels);
  }

  @Override
  public void receiveParcel(Parcel p) {
    LOGGER.info("{} receiveParcel {}", this, p);
    assignedParcels.add(p);
    eventDispatcher
    .dispatchEvent(new Event(CommunicatorEventType.CHANGE, this));
  }

  @Override
  public void releaseParcel(Parcel p) {
    LOGGER.info("{} releaseParcel {}", this, p);
    checkArgument(assignedParcels.contains(p));
    assignedParcels.remove(p);
    eventDispatcher
    .dispatchEvent(new Event(CommunicatorEventType.CHANGE, this));
  }

  @Override
  public final void init(RoadModel rm, PDPModel pm, Vehicle v) {
    roadModel = Optional.of((PDPRoadModel) rm);
    pdpModel = Optional.of(pm);
    vehicle = Optional.of(v);
    afterInit();
  }

  /**
   * This method can optionally be overridden to execute additional code right
   * after {@link #init(RoadModel, PDPModel, Vehicle)} is called.
   */
  protected void afterInit() {}

  @Override
  public String toString() {
    return toStringHelper(this).addValue(Integer.toHexString(hashCode()))
      .toString();
  }
}

package rinde.logistics.pdptw.mas.comm;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import rinde.logistics.pdptw.mas.Truck;
import rinde.logistics.pdptw.mas.TruckConfiguration;
import rinde.logistics.pdptw.mas.comm.CommunicationIntegrationTest.CommTestModel;
import rinde.logistics.pdptw.mas.comm.Communicator.CommunicatorEventType;
import rinde.logistics.pdptw.mas.route.AbstractRoutePlanner;
import rinde.sim.core.Simulator;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.PDPModel.ParcelState;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.event.Event;
import rinde.sim.pdptw.common.AddParcelEvent;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.DefaultVehicle;
import rinde.sim.pdptw.common.DynamicPDPTWProblem;
import rinde.sim.pdptw.common.PDPRoadModel;
import rinde.sim.pdptw.common.ParcelDTO;
import rinde.sim.pdptw.experiment.ExperimentTest;
import rinde.sim.pdptw.experiment.MASConfiguration;
import rinde.sim.pdptw.gendreau06.Gendreau06Parser;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenario;
import rinde.sim.util.SupplierRng;
import rinde.sim.util.SupplierRng.DefaultSupplierRng;
import rinde.sim.util.TimeWindow;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * Tests for the auction mechanism.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class AuctionTest {
  @SuppressWarnings("null")
  MASConfiguration configuration;
  @SuppressWarnings("null")
  AddParcelEvent ape1, ape2;
  @SuppressWarnings("null")
  Gendreau06Scenario scen;

  /**
   * 
   */
  @Before
  public void setUp() {
    configuration = new TruckConfiguration(
        FixedRoutePlanner.supplier(), RandomBidder.supplier(),
        ImmutableList.of(AuctionCommModel.supplier(), CommTestModel.supplier()));

    ape1 = new AddParcelEvent(ParcelDTO
        .builder(new Point(1, 1), new Point(1, 4))
        .pickupTimeWindow(new TimeWindow(218300, 10 * 60000))
        .deliveryTimeWindow(new TimeWindow(0, 20 * 60000))
        .serviceDuration(5000)
        .arrivalTime(-1)
        .build());

    ape2 = new AddParcelEvent(ParcelDTO
        .builder(new Point(4, 1), new Point(4, 4))
        .pickupTimeWindow(new TimeWindow(0, 10 * 60000))
        .deliveryTimeWindow(new TimeWindow(0, 20 * 60000))
        .serviceDuration(5000)
        .arrivalTime(-1)
        .build());

    scen = Gendreau06Parser.parser()
        .allowDiversion()
        .setNumVehicles(1)
        .addFile(ImmutableList.of(ape1, ape2), "req_rapide_1_240_24")
        .parse().get(0);
  }

  /**
   * Tests an auction scenario.
   */
  @Test
  public void test() {

    final DynamicPDPTWProblem problem = ExperimentTest.init(scen,
        configuration, 123, false);
    final Simulator sim = problem.getSimulator();
    sim.tick();

    final RoadModel rm = Optional.fromNullable(
        problem.getSimulator().getModelProvider().getModel(RoadModel.class))
        .get();

    final PDPModel pm = Optional.fromNullable(
        problem.getSimulator().getModelProvider().getModel(PDPModel.class))
        .get();

    final Set<Truck> trucks = rm.getObjectsOfType(Truck.class);

    final Truck truck = trucks.iterator().next();
    assertEquals(1, trucks.size());

    final Set<DefaultParcel> parcels = rm.getObjectsOfType(DefaultParcel.class);
    assertEquals(2, parcels.size());
    final Iterator<DefaultParcel> it = parcels.iterator();
    final DefaultParcel dp1 = it.next();
    assertEquals(ape1.parcelDTO, dp1.dto);
    final DefaultParcel dp2 = it.next();
    assertEquals(ape2.parcelDTO, dp2.dto);

    final FixedRoutePlanner routePlanner = (FixedRoutePlanner) truck
        .getRoutePlanner();

    final AbstractBidder bidder = (AbstractBidder) truck.getCommunicator();

    assertEquals(2, bidder.getParcels().size());
    assertTrue(bidder.getParcels().contains(dp1));
    assertTrue(bidder.getParcels().contains(dp2));
    assertTrue(bidder.getClaimedParcels().isEmpty());

    // set initial destination
    routePlanner.current = Optional.fromNullable(dp1);
    truck.handleEvent(new Event(CommunicatorEventType.CHANGE, this));
    sim.tick();

    assertThat(truck.getRoute().iterator().next(), is(dp1));
    assertThat(rm.getDestination(truck), is(dp1.dto.pickupLocation));
    assertThat(rm.getPosition(truck), is(not(truck.getDTO().startPosition)));

    assertEquals(2, bidder.getParcels().size());
    assertTrue(bidder.getParcels().contains(dp1));
    assertTrue(bidder.getParcels().contains(dp2));
    assertEquals(1, bidder.getClaimedParcels().size());
    assertTrue(bidder.getClaimedParcels().contains(dp1));

    // change destination
    routePlanner.current = Optional.fromNullable(dp2);
    truck.handleEvent(new Event(CommunicatorEventType.CHANGE, this));
    sim.tick();

    assertThat(truck.getRoute().iterator().next(), is(dp2));
    assertThat(rm.getDestination(truck), is(dp2.dto.pickupLocation));
    assertThat(rm.getPosition(truck), is(not(truck.getDTO().startPosition)));
    assertEquals(2, bidder.getParcels().size());
    assertTrue(bidder.getParcels().contains(dp1));
    assertTrue(bidder.getParcels().contains(dp2));
    assertEquals(1, bidder.getClaimedParcels().size());
    assertTrue(bidder.getClaimedParcels().contains(dp2));

    // change destination again, now back to first
    routePlanner.current = Optional.fromNullable(dp1);
    truck.handleEvent(new Event(CommunicatorEventType.CHANGE, this));
    sim.tick();

    assertThat(truck.getRoute().iterator().next(), is(dp1));
    assertThat(rm.getDestination(truck), is(dp1.dto.pickupLocation));
    assertThat(rm.getPosition(truck), is(not(truck.getDTO().startPosition)));

    while (!pm.getParcelState(dp1).isPickedUp()) {
      sim.tick();
    }
    routePlanner.current = Optional.fromNullable(dp2);
    while (rm.containsObject(dp2) && !rm.equalPosition(truck, dp2)) {
      sim.tick();
    }
  }

  /**
   * Illegal claim.
   */
  @Test(expected = IllegalArgumentException.class)
  public void claimFail1() {
    new RandomBidder(123).claim(new DefaultParcel(ape1.parcelDTO));
  }

  /**
   * Tests whether two consecutive calls to claim() throws an exception.
   */
  @Test
  public void claimFail2() {
    final RandomBidder rb = new RandomBidder(123);
    final DefaultParcel dp = new DefaultParcel(ape1.parcelDTO);
    final PDPModel pm = mock(PDPModel.class);
    rb.init(mock(PDPRoadModel.class), pm, mock(DefaultVehicle.class));
    when(pm.getParcelState(dp)).thenReturn(ParcelState.AVAILABLE);
    rb.receiveParcel(dp);
    rb.claim(dp);
    boolean fail = false;
    try {
      rb.claim(dp);
    } catch (final IllegalArgumentException e) {
      fail = true;
    }
    assertTrue(fail);
  }

  /**
   * Illegal unclaim.
   */
  @Test(expected = IllegalArgumentException.class)
  public void unclaimFail1() {
    new RandomBidder(123).unclaim(new DefaultParcel(ape1.parcelDTO));
  }

  /**
   * Illegal unclaim.
   */
  @Test(expected = IllegalArgumentException.class)
  public void unclaimFail2() {
    final RandomBidder rb = new RandomBidder(123);
    final DefaultParcel dp = new DefaultParcel(ape1.parcelDTO);
    rb.receiveParcel(dp);
    rb.unclaim(dp);
  }

  static class FixedRoutePlanner extends AbstractRoutePlanner {

    Optional<DefaultParcel> current;

    FixedRoutePlanner() {
      current = Optional.absent();
    }

    @Override
    public Optional<DefaultParcel> current() {
      return current;
    }

    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    protected void doUpdate(Collection<DefaultParcel> onMap, long time) {}

    @Override
    protected void nextImpl(long time) {
      current = Optional.absent();
    }

    static SupplierRng<FixedRoutePlanner> supplier() {
      return new DefaultSupplierRng<FixedRoutePlanner>() {
        @Override
        public FixedRoutePlanner get(long seed) {
          return new FixedRoutePlanner();
        }
      };
    }

  }

}
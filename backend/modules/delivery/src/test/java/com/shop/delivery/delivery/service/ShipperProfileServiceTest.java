package com.shop.delivery.delivery.service;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.repository.TelegramUserRepository;
import com.shop.delivery.auth.repository.UserRoleRepository;
import com.shop.delivery.auth.service.RoleResolver;
import com.shop.delivery.delivery.domain.ShipperState;
import com.shop.delivery.delivery.domain.VehicleType;
import com.shop.delivery.delivery.entity.ShipperProfile;
import com.shop.delivery.delivery.repository.ShipperProfileRepository;
import com.shop.delivery.delivery.service.command.CreateShipperCommand;
import com.shop.delivery.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipperProfileServiceTest {

    @Mock TelegramUserRepository userRepo;
    @Mock UserRoleRepository roleRepo;
    @Mock ShipperProfileRepository profileRepo;
    @Mock RoleResolver roleResolver;

    @InjectMocks ShipperProfileService service;

    @Test
    void createShipperShouldAssignRoleAndCreateProfile() {
        TelegramUser u = new TelegramUser();
        u.setId(8888L);
        u.setFirstName("Test Shipper");
        when(userRepo.findById(8888L)).thenReturn(Optional.of(u));
        when(roleRepo.existsByTelegramUserIdAndRoleAndStatus(8888L, Role.SHIPPER, UserRoleStatus.ACTIVE)).thenReturn(false);
        when(profileRepo.findById(8888L)).thenReturn(Optional.empty());
        when(profileRepo.save(any(ShipperProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        ShipperProfile result = service.createShipper(new CreateShipperCommand(
            8888L, VehicleType.MOTORBIKE, "29A-12345"));

        assertThat(result.getUserId()).isEqualTo(8888L);
        assertThat(result.getVehicleType()).isEqualTo(VehicleType.MOTORBIKE);
        assertThat(result.getLicensePlate()).isEqualTo("29A-12345");
        assertThat(result.getCurrentState()).isEqualTo(ShipperState.OFFLINE);

        ArgumentCaptor<UserRole> roleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(roleRepo).save(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getRole()).isEqualTo(Role.SHIPPER);
        assertThat(roleCaptor.getValue().getStatus()).isEqualTo(UserRoleStatus.ACTIVE);
    }

    @Test
    void createShipperShouldThrowIfTelegramUserNotFound() {
        when(userRepo.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createShipper(
            new CreateShipperCommand(9999L, VehicleType.MOTORBIKE, "X")
        )).isInstanceOf(NotFoundException.class);
    }

    @Test
    void setStateShouldUpdateProfile() {
        ShipperProfile p = new ShipperProfile();
        p.setUserId(8888L);
        p.setCurrentState(ShipperState.OFFLINE);
        when(profileRepo.findById(8888L)).thenReturn(Optional.of(p));
        when(profileRepo.save(any(ShipperProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setState(8888L, ShipperState.AVAILABLE);

        ArgumentCaptor<ShipperProfile> captor = ArgumentCaptor.forClass(ShipperProfile.class);
        verify(profileRepo).save(captor.capture());
        assertThat(captor.getValue().getCurrentState()).isEqualTo(ShipperState.AVAILABLE);
    }

    @Test
    void listAllShouldReturnAllShipperProfiles() {
        ShipperProfile p1 = new ShipperProfile();
        p1.setUserId(1L);
        ShipperProfile p2 = new ShipperProfile();
        p2.setUserId(2L);
        when(profileRepo.findAll()).thenReturn(List.of(p1, p2));

        List<ShipperProfile> result = service.listAll();
        assertThat(result).hasSize(2);
    }
}

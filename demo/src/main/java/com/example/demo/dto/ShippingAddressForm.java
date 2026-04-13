package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ShippingAddressForm {

    private Long id;

    @Size(max = 255, message = "Nhãn địa chỉ tối đa 255 ký tự")
    private String label;

    @NotBlank(message = "Vui lòng nhập tên người nhận")
    @Size(max = 120, message = "Tên người nhận tối đa 120 ký tự")
    private String recipientName;

    @NotBlank(message = "Vui lòng nhập số điện thoại người nhận")
    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    private String recipientPhone;

    @NotBlank(message = "Vui lòng nhập địa chỉ giao hàng")
    @Size(max = 1000, message = "Địa chỉ giao hàng quá dài")
    private String addressLine;

    private boolean defaultAddress = true;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public void setRecipientPhone(String recipientPhone) {
        this.recipientPhone = recipientPhone;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public boolean isDefaultAddress() {
        return defaultAddress;
    }

    public void setDefaultAddress(boolean defaultAddress) {
        this.defaultAddress = defaultAddress;
    }
}
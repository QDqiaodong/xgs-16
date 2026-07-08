package com.workspace.service;

import com.workspace.entity.OfficeFurniture;

import java.io.ByteArrayOutputStream;
import java.util.List;

public interface ExcelExportService {

    ByteArrayOutputStream exportFloorDetail(Integer floorNum, List<OfficeFurniture> furnitures);

    ByteArrayOutputStream exportAllFloorDetail(List<OfficeFurniture> furnitures);
}

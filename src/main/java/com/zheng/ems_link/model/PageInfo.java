package com.zheng.ems_link.model;

import java.io.Serializable;
import java.util.List;

public class PageInfo<T>  implements Serializable{

	
	/**
	 * 
	 */
	private static final long serialVersionUID = -5590205569113079677L;
	
	private int pageIndex;// 索引
	private int pageSize;// 每页显示条数

	private int totalPage;// 总页数

	private int dataCount;// 总记录数
	
	private List<T> data;

	public PageInfo() {
		
	}
	
	public PageInfo(int pageIndex, int pageSize, int dataCount) {
	        this.pageIndex = pageIndex;
	        this.dataCount = dataCount;
	        this.pageSize = pageSize;
	        this.totalPage = dataCount>0? (int) Math.ceil(dataCount * 1.0 / pageSize):1;
	  }
	 
		public int getPageIndex() {

			if (this.pageIndex <= 0) {
				this.pageIndex = 1;
			}
			if (this.pageIndex >= this.totalPage) {
				this.pageIndex = this.totalPage;
			}
			return pageIndex;
		}

	public void setPageIndex(int pageIndex) {
		this.pageIndex = pageIndex;
	}

	public int getPageSize() {
		return pageSize;
	}

	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}

	public int getTotalPage() {
		return totalPage;
	}

	public void setTotalPage(int totalPage) {
		this.totalPage = totalPage;
	}

	public int getDataCount() {
		return dataCount;
	}

	public void setDataCount(int dataCount) {
		this.dataCount = dataCount;
	}

	public List<T> getData() {
		return data;
	}

	public void setData(List<T> data) {
		this.data = data;
	}

}
